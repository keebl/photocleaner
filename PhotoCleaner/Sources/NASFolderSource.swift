import UIKit
import ImageIO
import AVFoundation
import UniformTypeIdentifiers

enum SourceError: LocalizedError {
    case notConfigured(String)
    var errorDescription: String? {
        switch self {
        case .notConfigured(let name): return "\(name) is nog niet ingesteld."
        }
    }
}

/// Foto's uit een map — bijv. een NAS-share die je in de iOS **Bestanden-app**
/// hebt gekoppeld (iOS ondersteunt daar SMB). Je kiest die map via de
/// documentkiezer; de toegang blijft bewaard via een security-scoped bookmark.
/// Zo blijft alles binnen een gewone iOS-app, zonder inloggegevens in de app.
final class NASFolderSource: PhotoSource {
    let displayName = "NAS-map"

    private let lock = NSLock()
    private var folderURL: URL?
    private var accessing = false
    private var cachedAll: [PhotoAsset]?

    private let thumbnailCache: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.countLimit = 12
        return cache
    }()
    private let hashCache = HashCache(filename: "nasHashes.json")

    private let bookmarkKey = "nasFolderBookmark"
    private let imageExtensions: Set<String> = ["jpg", "jpeg", "png", "heic", "heif", "tiff", "tif", "gif", "bmp"]
    private let videoExtensions: Set<String> = ["mp4", "mov", "m4v", "avi", "hevc", "3gp"]

    private func kind(for url: URL) -> MediaKind? {
        let ext = url.pathExtension.lowercased()
        if imageExtensions.contains(ext) { return .photo }
        if videoExtensions.contains(ext) { return .video }
        return nil
    }

    private static let exifFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy:MM:dd HH:mm:ss"
        f.timeZone = .current
        return f
    }()

    var hasFolder: Bool { folderURL != nil }
    var folderName: String? { folderURL?.lastPathComponent }

    // MARK: - Map kiezen / herstellen

    func setFolder(_ url: URL) {
        stopAccessing()
        if let data = try? url.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil) {
            UserDefaults.standard.set(data, forKey: bookmarkKey)
        }
        activate(url)
    }

    func restoreBookmark() {
        guard let data = UserDefaults.standard.data(forKey: bookmarkKey) else { return }
        var stale = false
        if let url = try? URL(resolvingBookmarkData: data, options: [], relativeTo: nil, bookmarkDataIsStale: &stale) {
            activate(url)
            // Verlopen bookmark? Vernieuw 'm zodat toegang behouden blijft.
            if stale, let fresh = try? url.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil) {
                UserDefaults.standard.set(fresh, forKey: bookmarkKey)
            }
        }
    }

    private func activate(_ url: URL) {
        accessing = url.startAccessingSecurityScopedResource()
        lock.lock(); folderURL = url; cachedAll = nil; lock.unlock()
    }

    /// Alleen voor tests: wijs rechtstreeks naar een lokale map (eigen container,
    /// geen security-scope nodig).
    func setTestFolder(_ url: URL) {
        accessing = false
        lock.lock(); folderURL = url; cachedAll = nil; lock.unlock()
    }

    private func stopAccessing() {
        if accessing, let folderURL { folderURL.stopAccessingSecurityScopedResource() }
        accessing = false
    }

    // MARK: - Ophalen

    func fetchAllPhotos() async -> [PhotoAsset] {
        lock.lock()
        if let cachedAll { lock.unlock(); return cachedAll }
        let folder = folderURL
        lock.unlock()

        guard let folder else { return [] }
        let assets = enumerate(folder)

        lock.lock(); cachedAll = assets; lock.unlock()
        return assets
    }

    func assets(withIDs ids: [String]) async -> [PhotoAsset] {
        ids.compactMap { id in
            guard let url = URL(string: id), FileManager.default.fileExists(atPath: url.path) else { return nil }
            return makeAsset(url)
        }
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
        lock.lock(); cachedAll = nil; lock.unlock()
    }

    func flushCaches() {
        hashCache.flush()
    }

    // MARK: - Thumbnails

    func cachedThumbnail(for asset: PhotoAsset, targetSize: CGSize) -> UIImage? {
        thumbnailCache.object(forKey: cacheKey(asset.id, targetSize))
    }

    func preload(_ assets: [PhotoAsset], targetSize: CGSize) {
        for asset in assets where cachedThumbnail(for: asset, targetSize: targetSize) == nil {
            Task { _ = await loadThumbnail(for: asset, targetSize: targetSize) }
        }
    }

    func loadThumbnail(for asset: PhotoAsset, targetSize: CGSize) async -> UIImage? {
        if let cached = cachedThumbnail(for: asset, targetSize: targetSize) { return cached }
        guard let url = URL(string: asset.id) else { return nil }

        let maxPixel = Int(max(targetSize.width, targetSize.height))
        let image = asset.isVideo
            ? Self.videoPoster(url, maxPixel: maxPixel)
            : Self.imageThumbnail(url, maxPixel: maxPixel)

        if let image {
            thumbnailCache.setObject(image, forKey: cacheKey(asset.id, targetSize))
        }
        return image
    }

    private static func imageThumbnail(_ url: URL, maxPixel: Int) -> UIImage? {
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixel,
            kCGImageSourceShouldCacheImmediately: true
        ]
        guard let src = CGImageSourceCreateWithURL(url as CFURL, nil),
              let cg = CGImageSourceCreateThumbnailAtIndex(src, 0, options as CFDictionary) else { return nil }
        return UIImage(cgImage: cg)
    }

    private static func videoPoster(_ url: URL, maxPixel: Int) -> UIImage? {
        let asset = AVURLAsset(url: url)
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: maxPixel, height: maxPixel)
        let time = CMTime(seconds: 0.5, preferredTimescale: 600)
        guard let cg = try? generator.copyCGImage(at: time, actualTime: nil) else { return nil }
        return UIImage(cgImage: cg)
    }

    func playerItem(for asset: PhotoAsset) async -> AVPlayerItem? {
        guard asset.isVideo, let url = URL(string: asset.id) else { return nil }
        return AVPlayerItem(url: url)
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
        for asset in assets {
            if asset.byteSize > 0 { result[asset.id] = asset.byteSize; continue }
            if let url = URL(string: asset.id),
               let values = try? url.resourceValues(forKeys: [.fileSizeKey]),
               let size = values.fileSize {
                result[asset.id] = Int64(size)
            }
        }
        return result
    }

    // MARK: - Verwijderen

    func delete(_ assets: [PhotoAsset]) async throws {
        for asset in assets {
            if let url = URL(string: asset.id) {
                try FileManager.default.removeItem(at: url)
            }
        }
        invalidateCache()
    }

    // MARK: - Hulpfuncties

    private func cacheKey(_ id: String, _ size: CGSize) -> NSString {
        "\(id)|\(Int(size.width))x\(Int(size.height))" as NSString
    }

    private func enumerate(_ folder: URL) -> [PhotoAsset] {
        let keys: [URLResourceKey] = [.isRegularFileKey, .fileSizeKey, .contentModificationDateKey, .creationDateKey]
        guard let enumerator = FileManager.default.enumerator(
            at: folder,
            includingPropertiesForKeys: keys,
            options: [.skipsHiddenFiles, .skipsPackageDescendants]
        ) else { return [] }

        var result: [PhotoAsset] = []
        for case let url as URL in enumerator {
            guard kind(for: url) != nil else { continue }
            result.append(makeAsset(url))
        }
        return result.sorted { ($0.creationDate ?? .distantPast) > ($1.creationDate ?? .distantPast) }
    }

    private func makeAsset(_ url: URL) -> PhotoAsset {
        let values = try? url.resourceValues(forKeys: [.fileSizeKey, .contentModificationDateKey, .creationDateKey])
        let byteSize = Int64(values?.fileSize ?? 0)
        let mediaKind = kind(for: url) ?? .photo
        // Video's hebben geen leesbare EXIF via ImageIO; gebruik dan de bestandsdatum.
        let meta = mediaKind == .photo ? metadata(url) : (date: nil, width: 0, height: 0)
        let creation = meta.date ?? values?.creationDate ?? values?.contentModificationDate
        return PhotoAsset(
            id: url.absoluteString,
            kind: mediaKind,
            creationDate: creation,
            modificationDate: values?.contentModificationDate,
            pixelWidth: meta.width,
            pixelHeight: meta.height,
            byteSize: byteSize,
            filename: url.lastPathComponent
        )
    }

    /// Leest EXIF-opnamedatum + afmetingen uit de bestandsheader (zonder de hele
    /// foto te decoderen).
    private func metadata(_ url: URL) -> (date: Date?, width: Int, height: Int) {
        guard let src = CGImageSourceCreateWithURL(url as CFURL, nil),
              let props = CGImageSourceCopyPropertiesAtIndex(src, 0, nil) as? [CFString: Any]
        else { return (nil, 0, 0) }

        let width = (props[kCGImagePropertyPixelWidth] as? Int) ?? 0
        let height = (props[kCGImagePropertyPixelHeight] as? Int) ?? 0
        var date: Date?
        if let exif = props[kCGImagePropertyExifDictionary] as? [CFString: Any],
           let raw = exif[kCGImagePropertyExifDateTimeOriginal] as? String {
            date = Self.exifFormatter.date(from: raw)
        }
        return (date, width, height)
    }
}
