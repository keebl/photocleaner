import SwiftUI

/// Toegangspoort + hoofdnavigatie. Zonder toegang tot de bibliotheek tonen we
/// de uitleg/knop; met toegang de drie tabbladen.
struct RootView: View {
    @EnvironmentObject private var library: PhotoLibrary
    @EnvironmentObject private var sources: SourceManager

    var body: some View {
        Group {
            if sources.kind == .iphone && !library.access.canReadPhotos {
                PermissionGateView()
            } else {
                MainTabs(source: sources.source)
                    .id(sources.kind)   // wissel van bron = verse view models
            }
        }
        .task {
            if sources.kind == .iphone && library.access == .notDetermined {
                await library.requestAccess()
            }
        }
    }
}

private struct MainTabs: View {
    let source: PhotoSource

    /// Begin-tab; standaard 0. Via de env-var START_TAB te sturen voor
    /// (screenshot)tests.
    @State private var selection: Int = Int(ProcessInfo.processInfo.environment["START_TAB"] ?? "") ?? 0

    var body: some View {
        TabView(selection: $selection) {
            OnThisDayView(source: source)
                .tabItem { Label("Op deze dag", systemImage: "calendar") }
                .tag(0)

            DuplicatesView(source: source)
                .tabItem { Label("Dubbelen", systemImage: "square.on.square") }
                .tag(1)

            TrashView(source: source)
                .tabItem { Label("Prullenbak", systemImage: "trash") }
                .tag(2)

            SettingsView()
                .tabItem { Label("Instellingen", systemImage: "gearshape") }
                .tag(3)
        }
    }
}
