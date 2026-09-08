import Photos
import UIKit

/// `PhotoSource`-implementatie bovenop Apple's PhotoKit (de iPhone-bibliotheek).
final class PhotoKitSource: PhotoSource {
    let displayName = "iPhone-bibliotheek"

    private let imageManager = PHCachingImageManager()

    /// Onthoudt de PHAsset achter elke `PhotoAsset.id`, zodat we later thumbnails
    /// kunnen laden en kunnen verwijderen zonder opnieuw de hele bibliotheek te
    /// doorzoeken.
    private var assetIndex: [String: PHAsset] = [:]

    // MARK: - Ophalen

    func fetchAllPhotos() async -> [PhotoAsset] {
        let options = PHFetchOptions()
        options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        options.predicate = NSPredicate(format: "mediaType == %d", PHAssetMediaType.image.rawValue)
        let result = PHAsset.fetchAssets(with: options)
        return mapAndIndex(result)
    }

    func fetchPhotos(onMonth month: Int, day: Int) async -> [PhotoAsset] {
        // PhotoKit kan niet direct op maand/dag filteren, dus filteren we in
        // geheugen op de creationDate. Prima voor een bibliotheek van deze schaal.
        let all = await fetchAllPhotos()
        let calendar = Calendar.current
        return all.filter { asset in
            guard let date = asset.creationDate else { return false }
            let comps = calendar.dateComponents([.month, .day], from: date)
            return comps.month == month && comps.day == day
        }
    }

    // MARK: - Thumbnails

    func loadThumbnail(for asset: PhotoAsset, targetSize: CGSize) async -> UIImage? {
        guard let phAsset = assetIndex[asset.id] else { return nil }
        let options = PHImageRequestOptions()
        options.deliveryMode = .highQualityFormat   // één callback, veilig voor async
        options.resizeMode = .fast
        options.isNetworkAccessAllowed = true        // haalt indien nodig uit iCloud

        return await withCheckedContinuation { continuation in
            var didResume = false
            imageManager.requestImage(
                for: phAsset,
                targetSize: targetSize,
                contentMode: .aspectFill,
                options: options
            ) { image, _ in
                guard !didResume else { return }
                didResume = true
                continuation.resume(returning: image)
            }
        }
    }

    // MARK: - Perceptual hash (lijkende foto's)

    func perceptualHash(for asset: PhotoAsset) async -> UInt64? {
        guard let phAsset = assetIndex[asset.id] else { return nil }
        let options = PHImageRequestOptions()
        options.deliveryMode = .highQualityFormat
        options.resizeMode = .exact
        options.isNetworkAccessAllowed = true

        let image: UIImage? = await withCheckedContinuation { continuation in
            var didResume = false
            imageManager.requestImage(
                for: phAsset,
                targetSize: CGSize(width: 32, height: 32),
                contentMode: .aspectFill,
                options: options
            ) { image, _ in
                guard !didResume else { return }
                didResume = true
                continuation.resume(returning: image)
            }
        }
        return Self.dHash(image)
    }

    /// dHash: teken op 9×8 grijswaarden en vergelijk elke pixel met z'n
    /// rechterbuur → 64 bits.
    private static func dHash(_ image: UIImage?) -> UInt64? {
        guard let cgImage = image?.cgImage else { return nil }
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
                let left = pixels[row * width + col]
                let right = pixels[row * width + col + 1]
                if left > right { hash |= (1 << bit) }
                bit += 1
            }
        }
        return hash
    }

    // MARK: - Verwijderen

    func delete(_ assets: [PhotoAsset]) async throws {
        let phAssets = assets.compactMap { assetIndex[$0.id] }
        guard !phAssets.isEmpty else { return }
        try await PHPhotoLibrary.shared().performChanges {
            PHAssetChangeRequest.deleteAssets(phAssets as NSArray)
        }
    }

    // MARK: - Hulpfuncties

    private func mapAndIndex(_ result: PHFetchResult<PHAsset>) -> [PhotoAsset] {
        var assets: [PhotoAsset] = []
        assets.reserveCapacity(result.count)
        result.enumerateObjects { phAsset, _, _ in
            self.assetIndex[phAsset.localIdentifier] = phAsset
            assets.append(self.map(phAsset))
        }
        return assets
    }

    private func map(_ phAsset: PHAsset) -> PhotoAsset {
        PhotoAsset(
            id: phAsset.localIdentifier,
            creationDate: phAsset.creationDate,
            pixelWidth: phAsset.pixelWidth,
            pixelHeight: phAsset.pixelHeight,
            byteSize: Self.byteSize(of: phAsset),
            filename: Self.filename(of: phAsset)
        )
    }

    /// Bestandsgrootte via de asset-resource. `fileSize` is een gangbare KVC-sleutel
    /// die PhotoKit hiervoor aanbiedt.
    // TODO(perf): voor zeer grote bibliotheken dit lui berekenen (alleen voor
    // kandidaat-dubbelen) i.p.v. voor élke foto tijdens de scan.
    private static func byteSize(of phAsset: PHAsset) -> Int64 {
        let resources = PHAssetResource.assetResources(for: phAsset)
        for resource in resources {
            if let size = resource.value(forKey: "fileSize") as? Int64 {
                return size
            }
        }
        return 0
    }

    private static func filename(of phAsset: PHAsset) -> String? {
        PHAssetResource.assetResources(for: phAsset).first?.originalFilename
    }
}
