import Photos
import UIKit
import CoreImage

/// `PhotoSource`-implementatie bovenop Apple's PhotoKit (de iPhone-bibliotheek).
final class PhotoKitSource: NSObject, PhotoSource, PHPhotoLibraryChangeObserver {
    let displayName = "iPhone-bibliotheek"

    private let imageManager = PHImageManager.default()

    /// In-memory cache van geladen thumbnails, zodat een volgende (voorgeladen)
    /// foto meteen verschijnt zonder "laden".
    private let thumbnailCache: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.countLimit = 12
        return cache
    }()

    private func cacheKey(_ id: String, _ size: CGSize) -> NSString {
        "\(id)|\(Int(size.width))x\(Int(size.height))" as NSString
    }

    /// Beschermt `assetIndex` en `cachedAll` tegen gelijktijdige toegang vanuit
    /// verschillende threads (fetch op de achtergrond vs. de wijzigingsobserver).
    private let stateLock = NSLock()
    /// Onthoudt de PHAsset achter elke `PhotoAsset.id`.
    private var assetIndex: [String: PHAsset] = [:]
    /// Cache van de volledige lijst, zodat tab-wissels en datumnavigatie niet
    /// telkens de hele bibliotheek opnieuw enumereren.
    private var cachedAll: [PhotoAsset]?
    /// Persistente cache van berekende perceptual hashes (per foto-id).
    private let hashCache = HashCache()

    override init() {
        super.init()
        PHPhotoLibrary.shared().register(self)
    }

    private var pendingChangeNotify: DispatchWorkItem?

    /// Automatisch verversen wanneer de bibliotheek wijzigt. Grote iCloud-
    /// bibliotheken sturen veel wijzigingen kort na elkaar; we voegen die samen
    /// (debounce) zodat de UI niet blijft herladen/knipperen.
    func photoLibraryDidChange(_ changeInstance: PHChange) {
        invalidateCache()
        DispatchQueue.main.async {
            self.pendingChangeNotify?.cancel()
            let work = DispatchWorkItem {
                NotificationCenter.default.post(name: .photoLibraryDidChange, object: nil)
            }
            self.pendingChangeNotify = work
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5, execute: work)
        }
    }

    // MARK: - Ophalen

    func fetchAllPhotos() async -> [PhotoAsset] {
        stateLock.lock()
        if let cachedAll { stateLock.unlock(); return cachedAll }
        stateLock.unlock()

        // Enumereren gebeurt BUITEN de lock (kan traag zijn bij grote
        // bibliotheken); we bouwen lokaal op en mergen daarna onder de lock.
        let options = PHFetchOptions()
        options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        options.predicate = NSPredicate(format: "mediaType == %d", PHAssetMediaType.image.rawValue)
        let (assets, index) = mapAndIndex(PHAsset.fetchAssets(with: options))

        stateLock.lock()
        for (key, value) in index { assetIndex[key] = value }
        cachedAll = assets
        stateLock.unlock()
        return assets
    }

    func assets(withIDs ids: [String]) async -> [PhotoAsset] {
        guard !ids.isEmpty else { return [] }
        let (assets, index) = mapAndIndex(PHAsset.fetchAssets(withLocalIdentifiers: ids, options: nil))
        stateLock.lock()
        for (key, value) in index { assetIndex[key] = value }
        stateLock.unlock()
        return assets
    }

    /// Thread-veilige opzoeking van de PHAsset achter een id.
    private func phAsset(for id: String) -> PHAsset? {
        stateLock.lock(); defer { stateLock.unlock() }
        return assetIndex[id]
    }

    func fetchPhotos(onMonth month: Int, day: Int) async -> [PhotoAsset] {
        // PhotoKit kan niet direct op maand/dag filteren; filter in geheugen op de
        // (gecachte) lijst. Prima voor een bibliotheek van deze schaal.
        let all = await fetchAllPhotos()
        let calendar = Calendar.current
        return all.filter { asset in
            guard let date = asset.creationDate else { return false }
            let comps = calendar.dateComponents([.month, .day], from: date)
            return comps.month == month && comps.day == day
        }
    }

    func invalidateCache() {
        stateLock.lock(); cachedAll = nil; stateLock.unlock()
    }

    func flushCaches() {
        hashCache.flush()
    }

    // MARK: - Thumbnails (annuleerbaar)

    /// Synchrone cache-opzoeking (voor directe weergave zonder "laden").
    func cachedThumbnail(for asset: PhotoAsset, targetSize: CGSize) -> UIImage? {
        thumbnailCache.object(forKey: cacheKey(asset.id, targetSize))
    }

    /// Laadt vast de thumbnails van de opgegeven foto's in de cache (bijv. de
    /// volgende foto's in de swipe-stapel), zodat ze meteen klaarstaan.
    func preload(_ assets: [PhotoAsset], targetSize: CGSize) {
        for asset in assets where cachedThumbnail(for: asset, targetSize: targetSize) == nil {
            Task { _ = await loadThumbnail(for: asset, targetSize: targetSize) }
        }
    }

    func loadThumbnail(for asset: PhotoAsset, targetSize: CGSize) async -> UIImage? {
        if let cached = cachedThumbnail(for: asset, targetSize: targetSize) { return cached }
        guard let phAsset = phAsset(for: asset.id) else { return nil }
        let options = PHImageRequestOptions()
        options.deliveryMode = .highQualityFormat
        options.resizeMode = .fast
        options.isNetworkAccessAllowed = true

        let box = RequestBox(manager: imageManager)
        let image: UIImage? = await withTaskCancellationHandler {
            await withCheckedContinuation { continuation in
                let id = imageManager.requestImage(
                    for: phAsset,
                    targetSize: targetSize,
                    contentMode: .aspectFill,
                    options: options
                ) { image, _ in
                    box.finish { continuation.resume(returning: image) }
                }
                box.store(id)
            }
        } onCancel: {
            // Scrolt de foto uit beeld? Annuleer het (mogelijk zware) verzoek.
            box.cancel()
        }

        if let image {
            thumbnailCache.setObject(image, forKey: cacheKey(asset.id, targetSize))
        }
        return image
    }

    // MARK: - Perceptual hash (lijkende foto's)

    func perceptualHash(for asset: PhotoAsset) async -> UInt64? {
        if let cached = hashCache.hash(for: asset.id, modifiedAt: asset.modificationDate) {
            return cached
        }
        guard let phAsset = phAsset(for: asset.id) else { return nil }

        let options = PHImageRequestOptions()
        // Lokaal een klein beeld (laten) genereren, maar NOOIT uit iCloud
        // downloaden — dat downloaden was de grote warmte-/databoosdoener.
        options.deliveryMode = .highQualityFormat
        options.resizeMode = .fast
        options.isNetworkAccessAllowed = false
        options.isSynchronous = false

        let box = RequestBox(manager: imageManager)
        let image: UIImage? = await withTaskCancellationHandler {
            await withCheckedContinuation { continuation in
                let id = imageManager.requestImage(
                    for: phAsset,
                    targetSize: CGSize(width: 32, height: 32),
                    contentMode: .aspectFill,
                    options: options
                ) { image, _ in
                    box.finish { continuation.resume(returning: image) }
                }
                box.store(id)
            }
        } onCancel: {
            box.cancel()
        }

        guard let hash = Self.dHash(image) else { return nil }
        hashCache.set(hash, for: asset.id, modifiedAt: asset.modificationDate)
        return hash
    }

    private static let ciContext = CIContext(options: [.useSoftwareRenderer: false])

    /// Haalt een `CGImage` uit een UIImage, ook als die door `fastFormat`
    /// CIImage-backed is (dan is `.cgImage` nil).
    private static func cgImage(from image: UIImage) -> CGImage? {
        if let cg = image.cgImage { return cg }
        if let ci = image.ciImage {
            return ciContext.createCGImage(ci, from: ci.extent)
        }
        return nil
    }

    /// dHash: teken op 9×8 grijswaarden en vergelijk elke pixel met z'n
    /// rechterbuur → 64 bits.
    private static func dHash(_ image: UIImage?) -> UInt64? {
        guard let image, let cgImage = cgImage(from: image) else { return nil }
        let width = 9, height = 8
        var pixels = [UInt8](repeating: 0, count: width * height)
        let colorSpace = CGColorSpaceCreateDeviceGray()
        guard let context = CGContext(
            data: &pixels,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.none.rawValue
        ) else { return nil }

        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))

        var hash: UInt64 = 0
        var bit: UInt64 = 0
        for row in 0..<height {
            for col in 0..<(width - 1) {
                if pixels[row * width + col] > pixels[row * width + col + 1] {
                    hash |= (1 << bit)
                }
                bit += 1
            }
        }
        return hash
    }

    // MARK: - Bestandsgrootte (lui, alleen voor kleine sets)

    func byteSizes(for assets: [PhotoAsset]) async -> [String: Int64] {
        var result: [String: Int64] = [:]
        for asset in assets {
            guard let phAsset = phAsset(for: asset.id) else { continue }
            for resource in PHAssetResource.assetResources(for: phAsset) {
                if let size = resource.value(forKey: "fileSize") as? Int64 {
                    result[asset.id] = size
                    break
                }
            }
        }
        return result
    }

    // MARK: - Verwijderen

    func delete(_ assets: [PhotoAsset]) async throws {
        let phAssets = assets.compactMap { phAsset(for: $0.id) }
        guard !phAssets.isEmpty else { return }
        try await PHPhotoLibrary.shared().performChanges {
            PHAssetChangeRequest.deleteAssets(phAssets as NSArray)
        }
        invalidateCache()
    }

    // MARK: - Hulpfuncties

    /// Bouwt (buiten de lock) een lijst PhotoAssets + een lokale index op.
    private func mapAndIndex(_ result: PHFetchResult<PHAsset>) -> (assets: [PhotoAsset], index: [String: PHAsset]) {
        var assets: [PhotoAsset] = []
        var index: [String: PHAsset] = [:]
        assets.reserveCapacity(result.count)
        result.enumerateObjects { phAsset, _, _ in
            index[phAsset.localIdentifier] = phAsset
            assets.append(self.map(phAsset))
        }
        return (assets, index)
    }

    /// Snelle map: alleen goedkope eigenschappen. Bestandsgrootte/naam vragen een
    /// trage `PHAssetResource`-call en berekenen we daarom lui via `byteSizes(for:)`.
    private func map(_ phAsset: PHAsset) -> PhotoAsset {
        PhotoAsset(
            id: phAsset.localIdentifier,
            creationDate: phAsset.creationDate,
            modificationDate: phAsset.modificationDate,
            pixelWidth: phAsset.pixelWidth,
            pixelHeight: phAsset.pixelHeight,
            byteSize: 0,
            filename: nil
        )
    }
}

/// Kleine thread-veilige houder voor een PhotoKit-verzoek, zodat we het kunnen
/// annuleren als de bijbehorende Task wordt afgebroken (bijv. bij scrollen).
private final class RequestBox {
    private let manager: PHImageManager
    private let lock = NSLock()
    private var requestID: PHImageRequestID?
    private var cancelled = false
    private var finished = false

    init(manager: PHImageManager) { self.manager = manager }

    func store(_ id: PHImageRequestID) {
        lock.lock(); defer { lock.unlock() }
        if cancelled { manager.cancelImageRequest(id) } else { requestID = id }
    }

    func cancel() {
        lock.lock(); defer { lock.unlock() }
        cancelled = true
        if let requestID { manager.cancelImageRequest(requestID) }
    }

    /// Voert de resume precies één keer uit.
    func finish(_ resume: () -> Void) {
        lock.lock()
        if finished { lock.unlock(); return }
        finished = true
        lock.unlock()
        resume()
    }
}
