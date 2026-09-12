import SwiftUI

/// Deel-knop voor één foto of video. Laadt het te delen bestand en toont het
/// systeem-deelvenster.
struct ShareButton: View {
    let asset: PhotoAsset
    let source: PhotoSource
    var tint: Color = .white

    @State private var payload: SharePayload?
    @State private var loading = false

    var body: some View {
        Button {
            guard !loading else { return }
            loading = true
            Task {
                let items = await source.shareItems(for: asset)
                loading = false
                if !items.isEmpty { payload = SharePayload(items: items) }
            }
        } label: {
            if loading {
                ProgressView().tint(tint)
            } else {
                Image(systemName: "square.and.arrow.up")
            }
        }
        .accessibilityLabel("Delen")
        .sheet(item: $payload) { payload in
            ShareSheet(items: payload.items)
        }
    }
}

struct SharePayload: Identifiable {
    let id = UUID()
    let items: [Any]
}

/// Wrapper om het systeem-deelvenster (UIActivityViewController).
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
