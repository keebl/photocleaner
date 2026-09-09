import Foundation

/// De fotobronnen die de app (nu of later) kan gebruiken. De iPhone-bibliotheek
/// is actief; Google Foto's en NAS zijn voorbereid via de `PhotoSource`-abstractie.
enum SourceKind: String, CaseIterable, Identifiable {
    case iphone
    case nas

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .iphone: return "iPhone-bibliotheek"
        case .nas:    return "NAS-map"
        }
    }

    var systemImage: String {
        switch self {
        case .iphone: return "iphone"
        case .nas:    return "externaldrive.connected.to.line.below"
        }
    }
}
