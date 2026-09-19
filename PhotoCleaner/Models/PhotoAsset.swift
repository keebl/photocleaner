import Foundation

/// Bron-onafhankelijke weergave van één foto.
///
/// Bewust losgekoppeld van PhotoKit's `PHAsset`, zodat we later andere bronnen
/// (Google Foto's, een NAS-map, ...) op dezelfde `PhotoSource`-abstractie kunnen
/// aansluiten zonder de UI of de opschoon-logica te wijzigen.
enum MediaKind: String, Hashable, Codable {
    case photo
    case video
}

struct PhotoAsset: Identifiable, Hashable, Codable {
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
    /// Map waarin het bestand staat (alleen bij mapgebaseerde bronnen zoals NAS);
    /// handig om te zien wélke kopie je weggooit. `nil` bij PhotoKit.
    var folder: String? = nil

    var megapixels: Double {
        Double(pixelWidth * pixelHeight) / 1_000_000
    }

    var pixelCount: Int {
        pixelWidth * pixelHeight
    }

    var isVideo: Bool { kind == .video }

    /// Korte, leesbare opnamedatum, bijv. "12 aug 2021" (nil als onbekend).
    var dateText: String? {
        guard let date = creationDate else { return nil }
        return PhotoAsset.dateFormatter.string(from: date)
    }

    private static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "nl_NL")
        f.dateFormat = "d MMM yyyy"
        return f
    }()

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
            filename: filename,
            folder: folder
        )
    }
}
