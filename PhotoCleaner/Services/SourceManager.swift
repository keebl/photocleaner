import SwiftUI

/// Beheert de actieve fotobron (iPhone-bibliotheek of NAS via SMB) en onthoudt de
/// keuze tussen sessies.
@MainActor
final class SourceManager: ObservableObject {
    @Published private(set) var kind: SourceKind
    /// Korte naam van de gekoppelde NAS (nil = nog niet ingesteld).
    @Published private(set) var smbName: String?

    let iphone = PhotoKitSource()
    let smb = SMBSource()

    /// De op dit moment actieve bron.
    var source: PhotoSource { kind == .nas ? smb : iphone }

    /// Of er al een NAS is ingesteld.
    var hasSMB: Bool { smb.isConfigured }

    init() {
        let raw = UserDefaults.standard.string(forKey: "sourceKind")
        kind = raw.flatMap(SourceKind.init(rawValue:)) ?? .iphone

        smb.restore()
        smbName = smb.credentials?.displayName

        // NAS gekozen maar niet (meer) ingesteld? Val terug op de iPhone.
        if kind == .nas && !smb.isConfigured {
            kind = .iphone
        }
    }

    func select(_ kind: SourceKind) {
        guard kind != self.kind else { return }
        // Naar NAS wisselen kan alleen als die is ingesteld.
        if kind == .nas && !smb.isConfigured { return }
        self.kind = kind
        UserDefaults.standard.set(kind.rawValue, forKey: "sourceKind")
    }

    /// Maakt de (net verbonden) NAS de actieve bron.
    func activateSMB() {
        smbName = smb.credentials?.displayName
        kind = .nas
        UserDefaults.standard.set(SourceKind.nas.rawValue, forKey: "sourceKind")
    }

    /// Verbreekt de NAS-koppeling en gaat terug naar de iPhone-bibliotheek.
    func disconnectSMB() {
        smb.disconnect()
        smbName = nil
        select(.iphone)
        if kind != .iphone {
            kind = .iphone
            UserDefaults.standard.set(SourceKind.iphone.rawValue, forKey: "sourceKind")
        }
    }
}
