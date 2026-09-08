import UIKit

/// Toegang tot een fotoverzameling, onafhankelijk van waar de foto's staan.
///
/// `PhotoKitSource` is de eerste implementatie (de iPhone-bibliotheek). Later
/// kunnen `GooglePhotosSource`, `NASFolderSource`, ... hier ook op mappen.
protocol PhotoSource: AnyObject {
    /// Naam voor in de UI, bijv. "iPhone-bibliotheek".
    var displayName: String { get }

    /// Alle foto's uit de bron (nieuwste eerst).
    func fetchAllPhotos() async -> [PhotoAsset]

    /// Foto's die op een bepaalde maand/dag zijn gemaakt (over alle jaren heen).
    func fetchPhotos(onMonth month: Int, day: Int) async -> [PhotoAsset]

    /// Laadt een thumbnail voor weergave.
    func loadThumbnail(for asset: PhotoAsset, targetSize: CGSize) async -> UIImage?

    /// Perceptual hash (dHash) voor het vinden van *lijkende* foto's. `nil` als er
    /// geen beeld beschikbaar is.
    func perceptualHash(for asset: PhotoAsset) async -> UInt64?

    /// Verwijdert foto's definitief uit de bron. Bij PhotoKit belanden ze nog in
    /// Apple's "Recent verwijderd" (extra vangnet) en toont iOS een bevestiging.
    func delete(_ assets: [PhotoAsset]) async throws
}
