import SwiftUI

/// Rasterweergave voor snelle bulk-opschoning: tik meerdere foto's aan om weg te
/// gooien, houd ingedrukt om te vergroten. Alternatief voor de één-voor-één
/// veegstapel ([[ReviewDeck]]).
struct GridReviewView: View {
    let assets: [PhotoAsset]
    let source: PhotoSource
    /// Reden waarmee weggegooide items in de prullenbak komen.
    let reason: String
    var emptyTitle = "Niets te tonen"
    var emptyMessage = "Er zijn hier geen items."
    var onNext: (() -> Void)? = nil
    var nextLabel = "Volgende met foto's"

    @EnvironmentObject private var trash: TrashStore
    @EnvironmentObject private var keep: KeepStore

    @State private var selected: Set<String> = []
    @State private var inspecting: PhotoAsset?
    @State private var playing: PhotoAsset?

    private let columns = [GridItem(.adaptive(minimum: 108), spacing: 3)]
    private static let thumbSize = CGSize(width: 400, height: 400)

    private var queue: [PhotoAsset] {
        assets.filter { !keep.contains($0.id) && !trash.contains($0.id) }
    }

    var body: some View {
        Group {
            if queue.isEmpty {
                emptyState
            } else {
                grid
            }
        }
        .fullScreenCover(item: $inspecting) { PhotoZoomView(asset: $0, source: source) }
        .fullScreenCover(item: $playing) { VideoPlayerScreen(asset: $0, source: source) }
    }

    private var grid: some View {
        VStack(spacing: 0) {
            ScrollView {
                LazyVGrid(columns: columns, spacing: 3) {
                    ForEach(queue) { cell($0) }
                }
                .padding(3)
            }
            actionBar
        }
    }

    private func cell(_ asset: PhotoAsset) -> some View {
        let isSelected = selected.contains(asset.id)
        return PhotoThumbnail(asset: asset, source: source, targetSize: Self.thumbSize)
            .aspectRatio(1, contentMode: .fill)
            .frame(maxWidth: .infinity)
            .clipped()
            .overlay(alignment: .bottomTrailing) {
                if asset.isVideo {
                    Image(systemName: "play.circle.fill")
                        .foregroundStyle(.white, .black.opacity(0.4))
                        .padding(5)
                }
            }
            .overlay {
                if isSelected {
                    ZStack {
                        Color.red.opacity(0.28)
                        Image(systemName: "trash.circle.fill")
                            .font(.title)
                            .foregroundStyle(.white, .red)
                    }
                }
            }
            .contentShape(Rectangle())
            .onTapGesture { toggle(asset) }
            .onLongPressGesture { inspect(asset) }
            .accessibilityLabel(isSelected ? "Geselecteerd om weg te gooien" : (asset.isVideo ? "Video" : "Foto"))
            .accessibilityHint("Tik om te selecteren, houd ingedrukt om te vergroten")
    }

    @ViewBuilder
    private var actionBar: some View {
        if !selected.isEmpty {
            HStack {
                Button("Selectie wissen") { withAnimation { selected.removeAll() } }
                    .buttonStyle(.bordered)
                Spacer()
                Button(role: .destructive) {
                    deleteSelected()
                } label: {
                    Label("Gooi \(selected.count) weg", systemImage: "trash")
                }
                .buttonStyle(.borderedProminent)
            }
            .padding(.horizontal)
            .padding(.vertical, 10)
            .background(.bar)
        } else {
            Text("Tik foto's aan om weg te gooien · houd ingedrukt om te vergroten")
                .font(.caption).foregroundStyle(.secondary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(.bar)
        }
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            ContentUnavailableView(emptyTitle, systemImage: "sparkles", description: Text(emptyMessage))
            if let onNext {
                Button {
                    Haptics.tap()
                    onNext()
                } label: {
                    Label(nextLabel, systemImage: "arrow.forward.circle.fill")
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func toggle(_ asset: PhotoAsset) {
        Haptics.tap()
        if selected.contains(asset.id) { selected.remove(asset.id) }
        else { selected.insert(asset.id) }
    }

    private func inspect(_ asset: PhotoAsset) {
        if asset.isVideo { playing = asset } else { inspecting = asset }
    }

    private func deleteSelected() {
        Haptics.warning()
        let toDelete = queue.filter { selected.contains($0.id) }
        withAnimation {
            for asset in toDelete { trash.mark(asset, reason: reason) }
            selected.removeAll()
        }
    }
}
