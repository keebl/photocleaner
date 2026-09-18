import SwiftUI

/// Toegangspoort + hoofdnavigatie. Zonder toegang tot de bibliotheek tonen we
/// de uitleg/knop; met toegang de drie tabbladen.
struct RootView: View {
    @EnvironmentObject private var library: PhotoLibrary
    @EnvironmentObject private var sources: SourceManager

    @AppStorage("didOnboard") private var didOnboard = false

    var body: some View {
        Group {
            if sources.kind == .iphone && !library.access.canReadPhotos {
                PermissionGateView()
            } else {
                MainTabs(source: sources.source)
                    .id(sources.kind)   // wissel van bron = verse view models
            }
        }
        .fullScreenCover(isPresented: Binding(get: { !didOnboard }, set: { didOnboard = !$0 })) {
            OnboardingView { didOnboard = true }
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

    /// Actieve tab, bewaard zodat andere schermen ernaartoe kunnen navigeren
    /// (bijv. de teller → Prullenbak). Via env-var START_TAB te sturen voor tests.
    @AppStorage("selectedTab") private var selection = 0

    var body: some View {
        TabView(selection: $selection) {
            MediaView(source: source)
                .tabItem { Label("Media", systemImage: "photo.on.rectangle.angled") }
                .tag(0)

            TrashView(source: source)
                .tabItem { Label("Prullenbak", systemImage: "trash") }
                .tag(1)

            SettingsView()
                .tabItem { Label("Instellingen", systemImage: "gearshape") }
                .tag(2)
        }
        .onAppear {
            if let tab = ProcessInfo.processInfo.environment["START_TAB"].flatMap(Int.init) {
                selection = tab
            }
        }
    }
}
