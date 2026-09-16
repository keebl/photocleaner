import UIKit
import ImageIO
import AVFoundation
import AMSMB2

enum SMBError: LocalizedError {
    case notConfigured
    case badServer
    case connectFailed(String)

    var errorDescription: String? {
        switch self {
        case .notConfigured:      return "Nog geen NAS ingesteld."
        case .badServer:          return "Ongeldig serveradres."
        case .connectFailed(let m): return m
        }
    }
}

/// Verbindt rechtstreeks vanuit de app met een NAS via **SMB** (het protocol dat
/// vrijwel elke NAS spreekt). Je vult serveradres, share, gebruiker en wachtwoord
/// in de app in — geen omweg via de Bestanden-app meer. Het wachtwoord staat in
/// de Keychain, niet in de code of gewone opslag.
final class SMBSource: PhotoSource {
    let displayName = "NAS (SMB)"

    private let connector = SMBConnector()
    /// Pool van extra verbindingen zodat mappen listen én previews downloaden
    /// parallel kunnen (met bovengrens, zodat de NAS niet overbelast raakt).
    private let pool: SMBPool
    private let stateLock = NSLock()
    private var cachedAll: [PhotoAsset]?
    private var configuredCreds: SMBCredentials?

    init() {
        pool = SMBPool(connector: connector, max: 4)
    }

    private let thumbnailCache: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.countLimit = 200   // voorbeeldjes zijn klein; voorkomt herhaald downloaden
        return cache
    }()
    private let hashCache = HashCache(filename: "smbHashes.json")

    private let imageExtensions: Set<String> = [
        "jpg", "jpeg", "png", "heic", "heif", "tiff", "tif", "gif", "bmp", "webp", "avif",
        // RAW-formaten van veelgebruikte camera's
        "dng", "cr2", "cr3", "nef", "nrw", "arw", "srf", "sr2", "orf", "rw2", "raf", "pef", "raw"
    ]
    private let videoExtensions: Set<String> = ["mp4", "mov", "m4v", "avi", "hevc", "3gp", "mkv", "m2ts", "mts"]

    /// Mappen die we bij het doorzoeken overslaan: verborgen mappen en de speciale
    /// systeemmappen van NAS'en (Synology-thumbnails, prullenbak, snapshots).
    private func isSkippableFolder(_ name: String) -> Bool {
        if name.hasPrefix(".") { return true }
        let junk: Set<String> = ["@eaDir", "#recycle", "#snapshot", "@tmp", "@sharesnap", "lost+found"]
        return junk.contains(name)
    }

    // MARK: - Instellen / verbinden

    var isConfigured: Bool { stateLock.withLock { configuredCreds != nil } }
    var credentials: SMBCredentials? { stateLock.withLock { configuredCreds } }

    /// Herstelt een eerder opgeslagen configuratie bij het opstarten (verbindt pas
    /// lui bij het eerste gebruik).
    func restore() {
        guard let creds = SMBCredentials.load() else { return }
        stateLock.withLock { configuredCreds = creds; cachedAll = nil }
        Task { await connector.configure(creds) }
    }

    /// Test de verbinding met de opgegeven gegevens en bewaart ze pas bij succes.
    /// Gooit een leesbare fout als het misgaat (verkeerd wachtwoord, share, enz.).
    func connect(_ creds: SMBCredentials, password: String) async throws {
        try await connector.connectAndValidate(creds, password: password)
        creds.save()
        KeychainStore.set(password, for: SMBCredentials.keychainKey)
        clearDiskListing(for: creds)   // nieuwe koppeling → verse scan
        stateLock.withLock { configuredCreds = creds; cachedAll = nil }
        thumbnailCache.removeAllObjects()
        await pool.drain()             // pool opnieuw opbouwen met de nieuwe gegevens
    }

    /// Verbreekt en wist de configuratie volledig.
    func disconnect() {
        let old = stateLock.withLock { () -> SMBCredentials? in
            let c = configuredCreds; configuredCreds = nil; cachedAll = nil; return c
        }
        clearDiskListing(for: old)
        SMBCredentials.clear()
        thumbnailCache.removeAllObjects()
        Task { await connector.reset(); await pool.drain() }
    }

    // MARK: - Ophalen

    func fetchAllPhotos() async -> [PhotoAsset] {
        if let cached = stateLock.withLock({ cachedAll }) { return cached }
        guard let creds = stateLock.withLock({ configuredCreds }) else { return [] }

        // Schijfcache: na de eerste scan is heropenen (of naar 'Dubbelen' gaan)
        // vrijwel instant. 'Opnieuw scannen' of een verwijdering wist de cache.
        if let disk = loadDiskListing(for: creds) {
            stateLock.withLock { cachedAll = disk }
            return disk
        }

        let assets = await walk(creds)
        // Bij annulering (bijv. wisselen van bron tijdens de scan) geen halve
        // lijst cachen.
        guard !Task.isCancelled else { return assets }
        stateLock.withLock { cachedAll = assets }
        saveDiskListing(assets, for: creds)
        return assets
    }

    /// Doorzoekt de gekozen map + submappen. Per niveau worden alle mappen parallel
    /// gelezen; de verbindingspool begrenst hoeveel er echt tegelijk lopen.
    /// Onleesbare of verborgen (systeem)mappen worden overgeslagen i.p.v. de scan
    /// te laten falen. Stopt netjes bij annulering (bijv. bronwissel).
    private func walk(_ creds: SMBCredentials) async -> [PhotoAsset] {
        var assets: [PhotoAsset] = []
        var level = [creds.normalizedFolder]
        while !level.isEmpty {
            if Task.isCancelled { return assets }
            let dirs = level
            let found = await withTaskGroup(of: (files: [PhotoAsset], subs: [String]).self) { group in
                for dir in dirs {
                    group.addTask {
                        await self.pool.withConnection { client in
                            await self.listDirectory(client, path: dir)
                        } ?? (files: [], subs: [])
                    }
                }
                var files: [PhotoAsset] = []
                var subs: [String] = []
                for await result in group { files += result.files; subs += result.subs }
                return (files: files, subs: subs)
            }
            assets += found.files
            level = found.subs
        }
        assets.sort { ($0.creationDate ?? .distantPast) > ($1.creationDate ?? .distantPast) }
        return assets
    }

    /// Lijst één map (niet-recursief) en verdeel in bestanden en submappen.
    private func listDirectory(_ manager: SMB2Manager, path: String)
        async -> (files: [PhotoAsset], subs: [String]) {
        guard let entries = try? await manager.contentsOfDirectory(atPath: path, recursive: false)
        else { return ([], []) }
        var files: [PhotoAsset] = []
        var subs: [String] = []
        for entry in entries {
            guard let p = entry.path else { continue }
            if entry.isDirectory {
                if !isSkippableFolder(entry.name ?? "") { subs.append(p) }
            } else if let asset = makeAsset(from: entry) {
                files.append(asset)
            }
        }
        return (files, subs)
    }

    func assets(withIDs ids: [String]) async -> [PhotoAsset] {
        // Eerst uit de cache; anders per stuk de kenmerken ophalen.
        let cached = stateLock.withLock { cachedAll } ?? []
        let byID = Dictionary(cached.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })

        var result: [PhotoAsset] = []
        for id in ids {
            if let hit = byID[id] { result.append(hit); continue }
            guard let kind = kind(forPath: id) else { continue }
            if let client = try? await connector.client(),
               let attrs = try? await client.attributesOfItem(atPath: id) {
                result.append(PhotoAsset(
                    id: id,
                    kind: kind,
                    creationDate: attrs.contentModificationDate ?? attrs.creationDate,
                    modificationDate: attrs.contentModificationDate,
                    pixelWidth: 0, pixelHeight: 0,
                    byteSize: attrs.fileSize ?? 0,
                    filename: (id as NSString).lastPathComponent,
                    folder: Self.parentFolder(id)
                ))
            } else {
                // Minimaal, zodat het item in de prullenbak toch verwijderbaar blijft.
                result.append(PhotoAsset(
                    id: id, kind: kind, creationDate: nil, modificationDate: nil,
                    pixelWidth: 0, pixelHeight: 0, byteSize: 0,
                    filename: (id as NSString).lastPathComponent,
                    folder: Self.parentFolder(id)
                ))
            }
        }
        return result
    }

    func fetchPhotos(onMonth month: Int, day: Int) async -> [PhotoAsset] {
        let all = await fetchAllPhotos()
        let calendar = Calendar.current
        return all.filter { asset in
            guard let date = asset.creationDate else { return false }
            let comps = calendar.dateComponents([.month, .day], from: date)
            return comps.month == month && comps.day == day
        }
    }

    func invalidateCache() {
        let creds = stateLock.withLock { () -> SMBCredentials? in
            let c = configuredCreds; cachedAll = nil; return c
        }
        clearDiskListing(for: creds)
    }

    func flushCaches() {
        hashCache.flush()
    }

    // MARK: - Thumbnails

    func cachedThumbnail(for asset: PhotoAsset, targetSize: CGSize) -> UIImage? {
        thumbnailCache.object(forKey: cacheKey(asset.id, targetSize))
    }

    func preload(_ assets: [PhotoAsset], targetSize: CGSize) {
        for asset in assets where !asset.isVideo && cachedThumbnail(for: asset, targetSize: targetSize) == nil {
            Task { _ = await loadThumbnail(for: asset, targetSize: targetSize) }
        }
    }

    /// Hoeveel bytes we eerst van een bestand lezen om (indien aanwezig) de
    /// ingebedde miniatuur eruit te halen — scheelt enorm veel data over SMB.
    private static let thumbnailPrefixBytes = 512 * 1024

    func loadThumbnail(for asset: PhotoAsset, targetSize: CGSize) async -> UIImage? {
        if let cached = cachedThumbnail(for: asset, targetSize: targetSize) { return cached }
        // Video's: geen poster over het netwerk halen (te zwaar). De UI toont dan
        // een filmsymbool; afspelen downloadt het bestand wél op verzoek.
        guard !asset.isVideo else { return nil }

        let maxPixel = Int(max(targetSize.width, targetSize.height))

        // 1) Schijfcache: een eerder gegenereerde preview staat direct klaar, ook
        //    na herstart. Zo hoeft elke foto maar één keer van de NAS te komen.
        let diskURL = thumbURL(for: asset, maxPixel: maxPixel)
        if let image = UIImage(contentsOfFile: diskURL.path) {
            thumbnailCache.setObject(image, forKey: cacheKey(asset.id, targetSize))
            return image
        }

        guard let image = await downloadThumbnail(for: asset, maxPixel: maxPixel) else { return nil }
        thumbnailCache.setObject(image, forKey: cacheKey(asset.id, targetSize))
        writeThumb(image, to: diskURL)
        return image
    }

    /// Haalt (zo zuinig mogelijk) een preview van de NAS: eerst alleen het begin
    /// van het bestand voor de ingebedde miniatuur, anders het hele bestand.
    private func downloadThumbnail(for asset: PhotoAsset, maxPixel: Int) async -> UIImage? {
        // Past het hele (kleine) bestand in de prefix, dan kunnen we dat volledig
        // decoderen; anders alleen een reeds ingebedde miniatuur gebruiken.
        let wholeFileFits = asset.byteSize > 0 && asset.byteSize <= Int64(Self.thumbnailPrefixBytes)
        if let prefix = await readData(path: asset.id, maxBytes: Self.thumbnailPrefixBytes),
           let image = Self.imageThumbnail(from: prefix, maxPixel: maxPixel, allowFullDecode: wholeFileFits) {
            return image
        }
        // Geen ingebedde miniatuur (bijv. sommige HEIC) → toch het hele bestand.
        guard let data = await readData(path: asset.id) else { return nil }
        return Self.imageThumbnail(from: data, maxPixel: maxPixel, allowFullDecode: true)
    }

    private static func imageThumbnail(from data: Data, maxPixel: Int, allowFullDecode: Bool) -> UIImage? {
        guard let src = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
        let options: [CFString: Any] = [
            // Bij een prefix géén volledige decode forceren (data is dan onvolledig);
            // dan alleen een reeds ingebedde miniatuur gebruiken.
            kCGImageSourceCreateThumbnailFromImageAlways: allowFullDecode,
            kCGImageSourceCreateThumbnailFromImageIfAbsent: allowFullDecode,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixel,
            kCGImageSourceShouldCacheImmediately: true
        ]
        guard let cg = CGImageSourceCreateThumbnailAtIndex(src, 0, options as CFDictionary) else { return nil }
        return UIImage(cgImage: cg)
    }

    // MARK: - Preview-schijfcache

    private func thumbURL(for asset: PhotoAsset, maxPixel: Int) -> URL {
        let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("smbThumbs", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        // Modificatiedatum in de sleutel → wijzigt het bestand, dan verse preview.
        let mod = Int(asset.modificationDate?.timeIntervalSince1970 ?? 0)
        return dir.appendingPathComponent(Self.stableHash("\(asset.id)|\(maxPixel)|\(mod)") + ".jpg")
    }

    private func writeThumb(_ image: UIImage, to url: URL) {
        guard let data = image.jpegData(compressionQuality: 0.8) else { return }
        try? data.write(to: url, options: .atomic)
    }

    /// Stabiele hash (niet de per-sessie gerandomiseerde `hashValue`) voor
    /// bestandsnamen die tussen sessies gelijk moeten blijven.
    private static func stableHash(_ s: String) -> String {
        var h: UInt64 = 5381
        for b in s.utf8 { h = (h &* 33) ^ UInt64(b) }
        return String(h, radix: 16)
    }

    // MARK: - Video / delen

    func playerItem(for asset: PhotoAsset) async -> AVPlayerItem? {
        guard asset.isVideo, let url = await localCopy(of: asset) else { return nil }
        return AVPlayerItem(url: url)
    }

    func shareItems(for asset: PhotoAsset) async -> [Any] {
        guard let url = await localCopy(of: asset) else { return [] }
        return [url]
    }

    /// Downloadt een bestand naar een tijdelijke map en hergebruikt die kopie.
    private func localCopy(of asset: PhotoAsset) async -> URL? {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("smb", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let dest = dir.appendingPathComponent(safeFilename(for: asset))
        if FileManager.default.fileExists(atPath: dest.path) { return dest }
        guard let data = await readData(path: asset.id) else { return nil }
        do { try data.write(to: dest, options: .atomic); return dest } catch { return nil }
    }

    private func safeFilename(for asset: PhotoAsset) -> String {
        let ext = (asset.id as NSString).pathExtension
        let base = String(UInt(bitPattern: asset.id.hashValue))
        return ext.isEmpty ? base : "\(base).\(ext)"
    }

    // MARK: - Perceptual hash

    func perceptualHash(for asset: PhotoAsset) async -> UInt64? {
        if let cached = hashCache.hash(for: asset.id, modifiedAt: asset.modificationDate) { return cached }
        guard let image = await loadThumbnail(for: asset, targetSize: CGSize(width: 64, height: 64)),
              let cg = image.cgImage,
              let hash = ImageHashing.dHash(cg) else { return nil }
        hashCache.set(hash, for: asset.id, modifiedAt: asset.modificationDate)
        return hash
    }

    // MARK: - Bestandsgrootte

    func byteSizes(for assets: [PhotoAsset]) async -> [String: Int64] {
        var result: [String: Int64] = [:]
        for asset in assets where asset.byteSize > 0 {
            result[asset.id] = asset.byteSize
        }
        return result
    }

    // MARK: - Verwijderen

    func delete(_ assets: [PhotoAsset]) async throws {
        let client = try await connector.client()
        for asset in assets {
            try await client.removeItem(atPath: asset.id)
        }
        invalidateCache()
    }

    // MARK: - Hulpfuncties

    private func readData(path: String, maxBytes: Int? = nil) async -> Data? {
        await pool.withConnection { client in
            if let maxBytes {
                return try await client.contents(atPath: path, range: 0..<Int64(maxBytes))
            }
            return try await client.contents(atPath: path)
        }
    }

    private func cacheKey(_ id: String, _ size: CGSize) -> NSString {
        "\(id)|\(Int(size.width))x\(Int(size.height))" as NSString
    }

    private func kind(forPath path: String) -> MediaKind? {
        let ext = (path as NSString).pathExtension.lowercased()
        if imageExtensions.contains(ext) { return .photo }
        if videoExtensions.contains(ext) { return .video }
        return nil
    }

    private func makeAsset(from entry: [URLResourceKey: Any]) -> PhotoAsset? {
        guard !entry.isDirectory, let path = entry.path, let kind = kind(forPath: path) else { return nil }
        return PhotoAsset(
            id: path,
            kind: kind,
            creationDate: entry.contentModificationDate ?? entry.creationDate,
            modificationDate: entry.contentModificationDate,
            pixelWidth: 0, pixelHeight: 0,
            byteSize: entry.fileSize ?? 0,
            filename: entry.name ?? (path as NSString).lastPathComponent,
            folder: Self.parentFolder(path)
        )
    }

    /// De map waarin een bestand staat, relatief t.o.v. de share (zonder de
    /// bestandsnaam). Leeg pad (wortel van de share) → nil.
    static func parentFolder(_ path: String) -> String? {
        let dir = (path as NSString).deletingLastPathComponent
        let trimmed = dir.trimmingCharacters(in: CharacterSet(charactersIn: "/ "))
        return trimmed.isEmpty ? nil : trimmed
    }

    // MARK: - Schijfcache van de mappenlijst

    private func diskListingURL(for creds: SMBCredentials) -> URL {
        let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let raw = "\(creds.host)|\(creds.share)|\(creds.normalizedFolder)"
        let safe = String(raw.unicodeScalars.map {
            CharacterSet.alphanumerics.contains($0) ? Character($0) : "_"
        })
        return caches.appendingPathComponent("smbListing-\(safe).json")
    }

    private func loadDiskListing(for creds: SMBCredentials) -> [PhotoAsset]? {
        guard let data = try? Data(contentsOf: diskListingURL(for: creds)),
              let assets = try? JSONDecoder().decode([PhotoAsset].self, from: data),
              !assets.isEmpty else { return nil }
        return assets
    }

    private func saveDiskListing(_ assets: [PhotoAsset], for creds: SMBCredentials) {
        guard !assets.isEmpty, let data = try? JSONEncoder().encode(assets) else { return }
        try? data.write(to: diskListingURL(for: creds), options: .atomic)
    }

    private func clearDiskListing(for creds: SMBCredentials?) {
        guard let creds else { return }
        try? FileManager.default.removeItem(at: diskListingURL(for: creds))
    }

    /// Wist alle NAS-caches: mappenlijst + previews (op schijf én in geheugen). De
    /// koppeling en foto's blijven ongemoeid; de volgende scan/preview komt vers van
    /// de NAS. Handig om te testen of een echt-verse start soepel is.
    func clearCaches() {
        invalidateCache()                 // geheugen- en schijf-mappenlijst
        thumbnailCache.removeAllObjects() // previews in geheugen
        let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        try? FileManager.default.removeItem(at: caches.appendingPathComponent("smbThumbs", isDirectory: true))
    }
}

/// Kleine pool van SMB-verbindingen zodat meerdere leesacties (mappen listen,
/// previews downloaden) tegelijk kunnen lopen, met een harde bovengrens zodat de
/// NAS niet overbelast raakt. Verbindingen worden lui aangemaakt en hergebruikt.
private actor SMBPool {
    private let connector: SMBConnector
    private let maxConnections: Int
    private var idle: [SMB2Manager] = []
    private var created = 0
    private var waiters: [CheckedContinuation<SMB2Manager, Never>] = []

    init(connector: SMBConnector, max: Int) {
        self.connector = connector
        self.maxConnections = Swift.max(1, max)
    }

    /// Leent een verbinding, voert `body` uit en geeft de verbinding daarna terug.
    /// Levert `nil` als er geen verbinding kon worden opgezet of `body` faalt.
    func withConnection<T>(_ body: (SMB2Manager) async throws -> T) async -> T? {
        guard let client = await acquire() else { return nil }
        defer { release(client) }
        return try? await body(client)
    }

    /// Vergeet de inactieve verbindingen (na (dis)connect). In-gebruik-zijnde
    /// verbindingen worden bij teruggave weer opgenomen; drain gebeurt buiten een
    /// actieve scan, dus dat is in de praktijk niet problematisch.
    func drain() {
        idle.removeAll()
        created = waiters.isEmpty ? 0 : created
    }

    private func acquire() async -> SMB2Manager? {
        if let client = idle.popLast() { return client }
        if created < maxConnections {
            created += 1
            if let client = try? await connector.makeConnectedClient() { return client }
            created -= 1
            return nil
        }
        return await withCheckedContinuation { (cont: CheckedContinuation<SMB2Manager, Never>) in
            waiters.append(cont)
        }
    }

    private func release(_ client: SMB2Manager) {
        if !waiters.isEmpty {
            waiters.removeFirst().resume(returning: client)
        } else {
            idle.append(client)
        }
    }
}

/// Beheert de eigenlijke SMB2Manager-verbinding op één plek, en zorgt dat er nooit twee
/// verbindingen tegelijk worden opgezet.
private actor SMBConnector {
    private var client: SMB2Manager?
    private var creds: SMBCredentials?
    private var connecting: Task<SMB2Manager, Error>?

    func configure(_ c: SMBCredentials?) {
        creds = c
        client = nil
        connecting = nil
    }

    func reset() {
        client = nil
        connecting = nil
    }

    /// Maakt verbinding met de opgegeven gegevens en controleert of de share +
    /// (sub)map echt te lezen zijn.
    func connectAndValidate(_ c: SMBCredentials, password: String) async throws {
        let m = try Self.make(c, password: password)
        do {
            try await m.connectShare(name: c.share)
            _ = try await m.contentsOfDirectory(atPath: c.normalizedFolder)
        } catch {
            throw SMBError.connectFailed(Self.friendlyMessage(error))
        }
        client = m
        creds = c
        connecting = nil
    }

    /// Levert een verbonden client; verbindt lui op basis van de bewaarde gegevens.
    func client() async throws -> SMB2Manager {
        if let client { return client }
        if let connecting { return try await connecting.value }
        guard let creds else { throw SMBError.notConfigured }

        let task = Task { () throws -> SMB2Manager in
            let password = KeychainStore.get(SMBCredentials.keychainKey) ?? ""
            let m = try Self.make(creds, password: password)
            do {
                try await m.connectShare(name: creds.share)
            } catch {
                throw SMBError.connectFailed(Self.friendlyMessage(error))
            }
            return m
        }
        connecting = task
        do {
            let m = try await task.value
            client = m
            connecting = nil
            return m
        } catch {
            connecting = nil
            throw error
        }
    }

    /// Maakt een extra, zelfstandig verbonden client (voor de parallelle
    /// mappen-walk). Gebruikt dezelfde bewaarde gegevens als de hoofverbinding.
    func makeConnectedClient() async throws -> SMB2Manager {
        guard let creds else { throw SMBError.notConfigured }
        let password = KeychainStore.get(SMBCredentials.keychainKey) ?? ""
        let m = try Self.make(creds, password: password)
        try await m.connectShare(name: creds.share)
        return m
    }

    private static func make(_ c: SMBCredentials, password: String) throws -> SMB2Manager {
        guard let url = c.serverURL else { throw SMBError.badServer }
        let credential = c.username.isEmpty ? nil
            : URLCredential(user: c.username, password: password, persistence: .forSession)
        guard let m = SMB2Manager(url: url, domain: c.domain, credential: credential) else {
            throw SMBError.badServer
        }
        return m
    }

    private static func friendlyMessage(_ error: Error) -> String {
        let ns = error as NSError
        let text = ns.localizedDescription.lowercased()
        if text.contains("auth") || text.contains("password") || text.contains("logon") || ns.code == 13 {
            return "Inloggen mislukt — controleer gebruikersnaam en wachtwoord."
        }
        if text.contains("no such") || text.contains("not found") || text.contains("share") {
            return "Share of map niet gevonden — controleer de sharenaam."
        }
        if text.contains("connection") || text.contains("host") || text.contains("network") || text.contains("timed out") {
            return "Kan de server niet bereiken — controleer het adres en of je op hetzelfde netwerk zit."
        }
        return ns.localizedDescription
    }
}
