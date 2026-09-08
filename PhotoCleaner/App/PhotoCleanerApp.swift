import SwiftUI

@main
struct PhotoCleanerApp: App {
    @StateObject private var library = PhotoLibrary()
    @StateObject private var trash = TrashStore()

    /// De actieve fotobron. Nu vast de iPhone-bibliotheek; later kiesbaar
    /// (Google Foto's, NAS, ...).
    private let source: PhotoSource = PhotoKitSource()

    var body: some Scene {
        WindowGroup {
            RootView(source: source)
                .environmentObject(library)
                .environmentObject(trash)
        }
    }
}
