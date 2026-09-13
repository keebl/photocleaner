import Foundation

/// De fotobronnen die de app kan gebruiken: de iPhone-bibliotheek en een NAS die
/// je rechtstreeks in de app koppelt via SMB.
enum SourceKind: String, CaseIterable, Identifiable {
    case iphone
    case nas

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .iphone: return "iPhone-bibliotheek"
        case .nas:    return "NAS (SMB)"
        }
    }

    var systemImage: String {
        switch self {
        case .iphone: return "iphone"
        case .nas:    return "externaldrive.connected.to.line.below"
        }
    }
}
