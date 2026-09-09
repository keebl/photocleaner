import SwiftUI

/// Beheert de actieve fotobron (iPhone-bibliotheek of NAS-map) en onthoudt de
/// keuze + de gekozen NAS-map tussen sessies.
@MainActor
final class SourceManager: ObservableObject {
    @Published private(set) var kind: SourceKind
    /// Naam van de gekozen NAS-map (nil = nog geen map gekozen).
    @Published private(set) var nasFolderName: String?

    let iphone = PhotoKitSource()
    let nas = NASFolderSource()

    /// De op dit moment actieve bron.
    var source: PhotoSource { kind == .nas ? nas : iphone }

    init() {
        let raw = UserDefaults.standard.string(forKey: "sourceKind")
        kind = raw.flatMap(SourceKind.init(rawValue:)) ?? .iphone

        nas.restoreBookmark()

        // Test-haakje: rechtstreeks naar een lokale map wijzen.
        if let testPath = ProcessInfo.processInfo.environment["NAS_TEST_FOLDER"] {
            nas.setTestFolder(URL(fileURLWithPath: testPath))
            kind = .nas
        }

        nasFolderName = nas.folderName

        // NAS gekozen maar geen (geldige) map meer? Val terug op de iPhone.
        if kind == .nas && !nas.hasFolder {
            kind = .iphone
        }
    }

    func select(_ kind: SourceKind) {
        guard kind != self.kind else { return }
        // Naar NAS wisselen kan alleen met een gekozen map.
        if kind == .nas && !nas.hasFolder { return }
        self.kind = kind
        UserDefaults.standard.set(kind.rawValue, forKey: "sourceKind")
    }

    /// Zet (of wijzigt) de NAS-map en maakt die meteen de actieve bron.
    func setNASFolder(_ url: URL) {
        nas.setFolder(url)
        nasFolderName = nas.folderName
        kind = .nas
        UserDefaults.standard.set(SourceKind.nas.rawValue, forKey: "sourceKind")
    }
}
