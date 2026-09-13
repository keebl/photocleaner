import SwiftUI

@main
struct PhotoCleanerApp: App {
    @StateObject private var library = PhotoLibrary()
    @StateObject private var trash = TrashStore()
    @StateObject private var keep = KeepStore()
    @StateObject private var notifications = NotificationManager()
    @StateObject private var themeManager = ThemeManager()
    @StateObject private var sources = SourceManager()

    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(library)
                .environmentObject(trash)
                .environmentObject(keep)
                .environmentObject(notifications)
                .environmentObject(themeManager)
                .environmentObject(sources)
                .preferredColorScheme(themeManager.theme.colorScheme)
                .task { await notifications.sync() }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background {
                sources.iphone.flushCaches()
                sources.smb.flushCaches()
            }
        }
    }
}
