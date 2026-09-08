import SwiftUI

/// Toegangspoort + hoofdnavigatie. Zonder toegang tot de bibliotheek tonen we
/// de uitleg/knop; met toegang de drie tabbladen.
struct RootView: View {
    let source: PhotoSource

    @EnvironmentObject private var library: PhotoLibrary

    var body: some View {
        Group {
            if library.access.canReadPhotos {
                MainTabs(source: source)
            } else {
                PermissionGateView()
            }
        }
        .task {
            if library.access == .notDetermined {
                await library.requestAccess()
            }
        }
    }
}

private struct MainTabs: View {
    let source: PhotoSource

    var body: some View {
        TabView {
            OnThisDayView(source: source)
                .tabItem { Label("Op deze dag", systemImage: "calendar") }

            DuplicatesView(source: source)
                .tabItem { Label("Dubbelen", systemImage: "square.on.square") }

            TrashView(source: source)
                .tabItem { Label("Prullenbak", systemImage: "trash") }
        }
    }
}
