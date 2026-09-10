import Foundation

/// Bron-onafhankelijke weergave van één foto.
///
/// Bewust losgekoppeld van PhotoKit's `PHAsset`, zodat we later andere bronnen
/// (Google Foto's, een NAS-map, ...) op dezelfde `PhotoSource`-abstractie kunnen
/// aansluiten zonder de UI of de opschoon-logica te wijzigen.
enum MediaKind: String, Hashable {
    case photo
    case video
}

struct PhotoAsset: Identifiable, Hashable {
    /// Stabiele identifier binnen de bron (bij PhotoKit: `localIdentifier`).
    let id: String
    var kind: MediaKind = .photo
    let creationDate: Date?
    /// Laatste wijziging (voor het invalideren van gecachte hashes na bewerken).
    let modificationDate: Date?
    let pixelWidth: Int
    let pixelHeight: Int
    /// Bestandsgrootte in bytes; 0 wanneer (nog) onbekend.
    let byteSize: Int64
    let filename: String?

    var megapixels: Double {
        Double(pixelWidth * pixelHeight) / 1_000_000
    }

    var pixelCount: Int {
        pixelWidth * pixelHeight
    }

    var isVideo: Bool { kind == .video }

    /// Kopie met een ingevulde bestandsgrootte (lui berekend, alleen waar nodig).
    func withByteSize(_ size: Int64) -> PhotoAsset {
        PhotoAsset(
            id: id,
            kind: kind,
            creationDate: creationDate,
            modificationDate: modificationDate,
            pixelWidth: pixelWidth,
            pixelHeight: pixelHeight,
            byteSize: size,
            filename: filename
        )
    }
}
