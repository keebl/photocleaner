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
    private let stateLock = NSLock()
    private var cachedAll: [PhotoAsset]?
    private var configuredCreds: SMBCredentials?

    private let thumbnailCache: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.countLimit = 12
        return cache
    }()
    private let hashCache = HashCache(filename: "smbHashes.json")

    private let imageExtensions: Set<String> = ["jpg", "jpeg", "png", "heic", "heif", "tiff", "tif", "gif", "bmp", "webp"]
    private let videoExtensions: Set<String> = ["mp4", "mov", "m4v", "avi", "hevc", "3gp", "mkv"]

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
        stateLock.withLock { configuredCreds = creds; cachedAll = nil }
        thumbnailCache.removeAllObjects()
    }

    /// Verbreekt en wist de configuratie volledig.
    func disconnect() {
        SMBCredentials.clear()
        stateLock.withLock { configuredCreds = nil; cachedAll = nil }
        thumbnailCache.removeAllObjects()
        Task { await connector.reset() }
    }

    // MARK: - Ophalen

    func fetchAllPhotos() async -> [PhotoAsset] {
        if let cached = stateLock.withLock({ cachedAll }) { return cached }
        guard let creds = stateLock.withLock({ configuredCreds }) else { return [] }

        do {
            let client = try await connector.client()
            let entries = try await client.contentsOfDirectory(
                atPath: creds.normalizedFolder, recursive: true
            )
            let assets = entries.compactMap { makeAsset(from: $0) }
                .sorted { ($0.creationDate ?? .distantPast) > ($1.creationDate ?? .distantPast) }
            stateLock.withLock { cachedAll = assets }
            return assets
        } catch {
            return []
        }
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
                    filename: (id as NSString).lastPathComponent
                ))
            } else {
                // Minimaal, zodat het item in de prullenbak toch verwijderbaar blijft.
                result.append(PhotoAsset(
                    id: id, kind: kind, creationDate: nil, modificationDate: nil,
                    pixelWidth: 0, pixelHeight: 0, byteSize: 0,
                    filename: (id as NSString).lastPathComponent
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
        stateLock.withLock { cachedAll = nil }
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

    func loadThumbnail(for asset: PhotoAsset, targetSize: CGSize) async -> UIImage? {
        if let cached = cachedThumbnail(for: asset, targetSize: targetSize) { return cached }
        // Video's: geen poster over het netwerk halen (te zwaar). De UI toont dan
        // een filmsymbool; afspelen downloadt het bestand wél op verzoek.
        guard !asset.isVideo else { return nil }

        let maxPixel = Int(max(targetSize.width, targetSize.height))
        guard let data = await readData(path: asset.id),
              let image = Self.imageThumbnail(from: data, maxPixel: maxPixel) else { return nil }
        thumbnailCache.setObject(image, forKey: cacheKey(asset.id, targetSize))
        return image
    }

    private static func imageThumbnail(from data: Data, maxPixel: Int) -> UIImage? {
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixel,
            kCGImageSourceShouldCacheImmediately: true
        ]
        guard let src = CGImageSourceCreateWithData(data as CFData, nil),
              let cg = CGImageSourceCreateThumbnailAtIndex(src, 0, options as CFDictionary) else { return nil }
        return UIImage(cgImage: cg)
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

    private func readData(path: String) async -> Data? {
        do {
            let client = try await connector.client()
            return try await client.contents(atPath: path)
        } catch {
            return nil
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
            filename: entry.name ?? (path as NSString).lastPathComponent
        )
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
