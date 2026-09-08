import SwiftUI

/// Weergavekeuze van de app.
enum AppTheme: String, CaseIterable, Identifiable {
    case system
    case light
    case dark

    var id: String { rawValue }

    var label: String {
        switch self {
        case .system: return "Systeem"
        case .light:  return "Licht"
        case .dark:   return "Donker"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light:  return .light
        case .dark:   return .dark
        }
    }
}

/// Bewaart en levert de gekozen weergave. Standaard **Donker** (voorkeur gebruiker).
@MainActor
final class ThemeManager: ObservableObject {
    @Published var theme: AppTheme {
        didSet { UserDefaults.standard.set(theme.rawValue, forKey: "appTheme") }
    }

    init() {
        let raw = UserDefaults.standard.string(forKey: "appTheme")
        theme = raw.flatMap(AppTheme.init(rawValue:)) ?? .dark
    }
}
