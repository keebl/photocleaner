import Foundation

/// De fotobronnen die de app (nu of later) kan gebruiken. De iPhone-bibliotheek
/// is actief; Google Foto's en NAS zijn voorbereid via de `PhotoSource`-abstractie.
enum SourceKind: String, CaseIterable, Identifiable {
    case iphone
    case googlePhotos
    case nas

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .iphone:       return "iPhone-bibliotheek"
        case .googlePhotos: return "Google Foto's"
        case .nas:          return "NAS-map"
        }
    }

    var systemImage: String {
        switch self {
        case .iphone:       return "iphone"
        case .googlePhotos: return "cloud"
        case .nas:          return "externaldrive.connected.to.line.below"
        }
    }

    /// Nu alleen de iPhone-bron; de rest volgt.
    var isAvailable: Bool { self == .iphone }

    var statusText: String {
        switch self {
        case .iphone:       return "Actief"
        case .googlePhotos: return "Binnenkort — vereist Google-inlog"
        case .nas:          return "Binnenkort — vereist netwerkconfiguratie"
        }
    }
}
