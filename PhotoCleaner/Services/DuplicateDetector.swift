import Foundation

/// Een groep foto's die op basis van metadata als duplicaat wordt beschouwd.
struct DuplicateGroup: Identifiable {
    /// Stabiele id op basis van de te behouden foto, zodat SwiftUI de rij (en
    /// thumbnails) hergebruikt tussen scans i.p.v. opnieuw te laden.
    var id: String { keep.id }
    /// De foto die we aanraden te behouden (hoogste resolutie / grootste bestand).
    let keep: PhotoAsset
    /// De overige foto's in de groep — kandidaten om weg te gooien.
    let duplicates: [PhotoAsset]

    var all: [PhotoAsset] { [keep] + duplicates }
    var count: Int { all.count }

    /// Ruimte die je terugwint als je de duplicaten weggooit.
    var reclaimableBytes: Int64 { duplicates.reduce(0) { $0 + $1.byteSize } }

    /// Maakt een groep uit losse foto's: behoud de foto met de meeste pixels
    /// (bij gelijkspel het grootste bestand), de rest zijn duplicaten.
    static func make(from assets: [PhotoAsset]) -> DuplicateGroup {
        let sorted = assets.sorted { a, b in
            if a.pixelCount != b.pixelCount { return a.pixelCount > b.pixelCount }
            return a.byteSize > b.byteSize
        }
        return DuplicateGroup(keep: sorted[0], duplicates: Array(sorted.dropFirst()))
    }
}

/// Vindt duplicaten op basis van métadata (geen pixelvergelijking).
///
/// Signatuur = opnametijdstip (op de seconde) + afmetingen. Binnen een groep
/// verfijnen we optioneel met bestandsgrootte. Dit vangt de klassieke gevallen:
/// dezelfde foto meerdere keren geïmporteerd, of een export naast het origineel.
enum DuplicateDetector {
    static func findDuplicates(in assets: [PhotoAsset]) -> [DuplicateGroup] {
        var buckets: [String: [PhotoAsset]] = [:]

        for asset in assets {
            guard let key = signature(for: asset) else { continue }
            buckets[key, default: []].append(asset)
        }

        return buckets.values
            .filter { $0.count > 1 }
            .map { DuplicateGroup.make(from: $0) }
            .sorted { $0.reclaimableBytes > $1.reclaimableBytes }
    }

    /// Foto's zonder opnamedatum slaan we over: te riskant om zonder tijdstip als
    /// duplicaat aan te merken.
    private static func signature(for asset: PhotoAsset) -> String? {
        guard let date = asset.creationDate else { return nil }
        let second = Int(date.timeIntervalSince1970)
        return "\(second)|\(asset.pixelWidth)x\(asset.pixelHeight)"
    }
}
