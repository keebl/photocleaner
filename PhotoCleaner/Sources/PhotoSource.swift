import UIKit
import AVFoundation

extension Notification.Name {
    /// Wordt gepost als de onderliggende fotobibliotheek verandert.
    static let photoLibraryDidChange = Notification.Name("photoLibraryDidChange")
}

/// Toegang tot een fotoverzameling, onafhankelijk van waar de foto's staan.
///
/// `PhotoKitSource` is de eerste implementatie (de iPhone-bibliotheek). Later
/// kunnen `GooglePhotosSource`, `NASFolderSource`, ... hier ook op mappen.
protocol PhotoSource: AnyObject {
    /// Naam voor in de UI, bijv. "iPhone-bibliotheek".
    var displayName: String { get }

    /// Alle foto's uit de bron (nieuwste eerst). Mag intern cachen.
    func fetchAllPhotos() async -> [PhotoAsset]

    /// Alleen de foto's met de opgegeven id's (bijv. voor de prullenbak), zonder
    /// de hele bibliotheek te laden.
    func assets(withIDs ids: [String]) async -> [PhotoAsset]

    /// Gooit een eventuele interne cache weg (na wijzigingen/verwijderingen).
    func invalidateCache()

    /// Schrijft in-memory caches (zoals berekende hashes) weg naar schijf.
    func flushCaches()

    /// Foto's die op een bepaalde maand/dag zijn gemaakt (over alle jaren heen).
    func fetchPhotos(onMonth month: Int, day: Int) async -> [PhotoAsset]

    /// Laadt een thumbnail voor weergave.
    func loadThumbnail(for asset: PhotoAsset, targetSize: CGSize) async -> UIImage?

    /// Direct beschikbare (gecachte) thumbnail, of nil. Voor weergave zonder flits.
    func cachedThumbnail(for asset: PhotoAsset, targetSize: CGSize) -> UIImage?

    /// Laadt vast thumbnails in de cache (bijv. de volgende foto's).
    func preload(_ assets: [PhotoAsset], targetSize: CGSize)

    /// Speelbare video (nil voor foto's of als niet beschikbaar).
    func playerItem(for asset: PhotoAsset) async -> AVPlayerItem?

    /// Items om te delen (een bestand-URL of afbeelding). Leeg als niet beschikbaar.
    func shareItems(for asset: PhotoAsset) async -> [Any]

    /// Perceptual hash (dHash) voor het vinden van *lijkende* foto's. `nil` als er
    /// geen beeld beschikbaar is.
    func perceptualHash(for asset: PhotoAsset) async -> UInt64?

    /// Bestandsgrootte (bytes) per foto-id. Relatief traag; alleen voor kleine sets
    /// aanroepen (bijv. gevonden duplicaten), niet voor de hele bibliotheek.
    func byteSizes(for assets: [PhotoAsset]) async -> [String: Int64]

    /// Verwijdert foto's definitief uit de bron. Bij PhotoKit belanden ze nog in
    /// Apple's "Recent verwijderd" (extra vangnet) en toont iOS een bevestiging.
    func delete(_ assets: [PhotoAsset]) async throws
}
