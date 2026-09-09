import Foundation

/// Bron-onafhankelijke weergave van één foto.
///
/// Bewust losgekoppeld van PhotoKit's `PHAsset`, zodat we later andere bronnen
/// (Google Foto's, een NAS-map, ...) op dezelfde `PhotoSource`-abstractie kunnen
/// aansluiten zonder de UI of de opschoon-logica te wijzigen.
struct PhotoAsset: Identifiable, Hashable {
    /// Stabiele identifier binnen de bron (bij PhotoKit: `localIdentifier`).
    let id: String
    let creationDate: Date?
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

    /// Kopie met een ingevulde bestandsgrootte (lui berekend, alleen waar nodig).
    func withByteSize(_ size: Int64) -> PhotoAsset {
        PhotoAsset(
            id: id,
            creationDate: creationDate,
            pixelWidth: pixelWidth,
            pixelHeight: pixelHeight,
            byteSize: size,
            filename: filename
        )
    }
}
