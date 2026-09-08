import SwiftUI

/// Getoond wanneer de app (nog) geen toegang tot de foto's heeft.
struct PermissionGateView: View {
    @EnvironmentObject private var library: PhotoLibrary

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "photo.on.rectangle.angled")
                .font(.system(size: 64))
                .foregroundStyle(.tint)

            Text("Toegang tot je foto's")
                .font(.title2).bold()

            Text("PhotoCleaner heeft toegang nodig om dubbelen te vinden en je 'op deze dag'-herinneringen te tonen. Je foto's blijven op je toestel.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .padding(.horizontal)

            if library.access == .denied {
                Button("Open Instellingen") { library.openSettings() }
                    .buttonStyle(.borderedProminent)
            } else {
                Button("Geef toegang") {
                    Task { await library.requestAccess() }
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding()
    }
}
