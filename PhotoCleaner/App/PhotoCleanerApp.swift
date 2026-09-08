import SwiftUI

@main
struct PhotoCleanerApp: App {
    @StateObject private var library = PhotoLibrary()
    @StateObject private var trash = TrashStore()
    @StateObject private var notifications = NotificationManager()
    @StateObject private var themeManager = ThemeManager()

    /// De actieve fotobron. Nu vast de iPhone-bibliotheek; later kiesbaar
    /// (Google Foto's, NAS, ...).
    private let source: PhotoSource = PhotoKitSource()

    var body: some Scene {
        WindowGroup {
            RootView(source: source)
                .environmentObject(library)
                .environmentObject(trash)
                .environmentObject(notifications)
                .environmentObject(themeManager)
                .preferredColorScheme(themeManager.theme.colorScheme)
                .task { await notifications.sync() }
        }
    }
}
