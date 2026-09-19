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

    private static let spacing: CGFloat = 3
    private let columns = Array(repeating: GridItem(.flexible(), spacing: spacing), count: 2)
    private static let thumbSize = CGSize(width: 800, height: 800)

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
            GeometryReader { geo in
                ScrollView {
                    LazyVGrid(columns: columns, spacing: Self.spacing) {
                        ForEach(queue) { cell($0, height: tileHeight(in: geo.size)) }
                    }
                    .padding(Self.spacing)
                }
            }
            actionBar
        }
    }

    /// Hoogte per tegel zodat er precies 2 rijen (dus 2×2 = 4 foto's) op het scherm
    /// passen; bij meer foto's scrol je verder.
    private func tileHeight(in size: CGSize) -> CGFloat {
        max(120, (size.height - Self.spacing * 3) / 2)
    }

    private func cell(_ asset: PhotoAsset, height: CGFloat) -> some View {
        let isSelected = selected.contains(asset.id)
        // Vaste rechthoekige tegel (2 kolommen, 2 rijen op het scherm): de foto vult
        // 'm bijgesneden — zo blijft het raster strak, ongeacht liggend/staand.
        return Color.clear
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .overlay {
                PhotoThumbnail(asset: asset, source: source, targetSize: Self.thumbSize, contentMode: .fill)
            }
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay {
                if isSelected {
                    RoundedRectangle(cornerRadius: 8).fill(Color.accentColor.opacity(0.25))
                    RoundedRectangle(cornerRadius: 8).strokeBorder(Color.accentColor, lineWidth: 3)
                }
            }
            .overlay(alignment: .bottomTrailing) {
                if asset.isVideo {
                    Image(systemName: "play.circle.fill")
                        .foregroundStyle(.white, .black.opacity(0.4))
                        .padding(4)
                }
            }
            .overlay(alignment: .topLeading) { selectToggle(asset, isSelected: isSelected) }
            .contentShape(Rectangle())
            .onTapGesture { inspect(asset) }
            .accessibilityLabel(asset.isVideo ? "Video" : "Foto")
            .accessibilityHint("Tik om te vergroten; gebruik het rondje om te selecteren")
    }

    /// Rondje linksboven: aan = geselecteerd (kies daarna behouden of weggooien).
    private func selectToggle(_ asset: PhotoAsset, isSelected: Bool) -> some View {
        Button { toggle(asset) } label: {
            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                .font(.headline)
                .symbolRenderingMode(.palette)
                .foregroundStyle(.white, isSelected ? Color.accentColor : Color.black.opacity(0.35))
                .background(Circle().fill(.black.opacity(0.25)))
                .padding(4)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isSelected ? "Geselecteerd, tik om te annuleren" : "Selecteer")
    }

    @ViewBuilder
    private var actionBar: some View {
        if !selected.isEmpty {
            HStack(spacing: 12) {
                Button { keepSelected() } label: {
                    Label("Behoud \(selected.count)", systemImage: "checkmark")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .tint(.green)

                Button(role: .destructive) {
                    deleteSelected()
                } label: {
                    Label("Gooi \(selected.count) weg", systemImage: "trash")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
            }
            .padding(.horizontal)
            .padding(.vertical, 10)
            .background(.bar)
        } else {
            Text("Tik een foto om te bekijken · selecteer met het rondje om te behouden of weg te gooien")
                .font(.caption).foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.horizontal)
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

    private func keepSelected() {
        Haptics.tap()
        let toKeep = queue.filter { selected.contains($0.id) }
        withAnimation {
            for asset in toKeep { keep.keep(asset.id) }
            selected.removeAll()
        }
    }
}
