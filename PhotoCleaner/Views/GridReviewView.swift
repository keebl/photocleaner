import SwiftUI

/// Rasterweergave voor snelle bulk-opschoning: tik meerdere foto's aan om weg te
/// gooien, houd ingedrukt om te vergroten. Alternatief voor de één-voor-één
/// veegstapel ([[ReviewDeck]]).
struct GridReviewView: View {
    let assets: [PhotoAsset]
    let source: PhotoSource
    /// Reden waarmee weggegooide items in de prullenbak komen.
    let reason: String
    /// Zelfstandig naamwoord voor de teller, bijv. "foto's" of "filmpjes".
    var itemNoun = "items"
    var emptyTitle = "Niets te tonen"
    var emptyMessage = "Er zijn hier geen items."
    var onNext: (() -> Void)? = nil
    var nextLabel = "Volgende met foto's"

    @EnvironmentObject private var trash: TrashStore
    @EnvironmentObject private var keep: KeepStore

    @State private var selected: Set<String> = []
    @State private var inspecting: PhotoAsset?
    @State private var playing: PhotoAsset?
    /// Laatste bulk-actie (behouden of weggooien), zodat je 'm ongedaan kunt maken.
    @State private var lastAction: [(id: String, kept: Bool)] = []

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
        .fullScreenCover(item: $inspecting) { asset in
            PhotoZoomView(
                asset: asset, source: source,
                onKeep: {
                    keep.keep(asset.id)
                    selected.remove(asset.id)
                },
                onDiscard: {
                    trash.mark(asset, reason: reason)
                    selected.remove(asset.id)
                }
            )
        }
        .fullScreenCover(item: $playing) { asset in
            VideoPlayerScreen(
                asset: asset, source: source,
                onKeep: {
                    keep.keep(asset.id)
                    selected.remove(asset.id)
                },
                onDiscard: {
                    trash.mark(asset, reason: reason)
                    selected.remove(asset.id)
                }
            )
        }
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
            .overlay(alignment: .bottomLeading) {
                if let dateText = asset.dateText {
                    Text(dateText)
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 6).padding(.vertical, 3)
                        .background(.black.opacity(0.45), in: Capsule())
                        .padding(6)
                }
            }
            .overlay(alignment: .topTrailing) { selectToggle(asset, isSelected: isSelected) }
            .contentShape(Rectangle())
            .onTapGesture { inspect(asset) }
            .accessibilityLabel(asset.isVideo ? "Video" : "Foto")
            .accessibilityHint("Tik om te vergroten; gebruik het rondje om te selecteren")
    }

    /// Rondje rechtsboven: aan = geselecteerd (kies daarna behouden of weggooien).
    /// Bewust rechtsboven i.p.v. linksboven: langs de linker schermrand houdt iOS
    /// aanrakingen ~2s vast voor het terug-veeggebaar ("system gesture gate"),
    /// waardoor het vinkje pas veel later verscheen. Een ruime trefzone maakt 'm
    /// bovendien makkelijker te raken.
    private func selectToggle(_ asset: PhotoAsset, isSelected: Bool) -> some View {
        Button { toggle(asset) } label: {
            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                .font(.title3)
                .symbolRenderingMode(.palette)
                .foregroundStyle(.white, isSelected ? Color.accentColor : Color.black.opacity(0.35))
                .background(Circle().fill(.black.opacity(0.25)))
                .padding(10)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isSelected ? "Geselecteerd, tik om te annuleren" : "Selecteer")
    }

    private var allSelected: Bool { !queue.isEmpty && selected.count == queue.count }

    private var actionBar: some View {
        VStack(spacing: 8) {
            // Actierij met vaste opmaak: de knoppen staan er altijd (uitgeschakeld
            // zonder selectie), zodat het raster niet verspringt en de thumbnails
            // niet herladen zodra je de eerste foto aantikt. Volgorde in lijn met
            // vegen: weggooien links, behouden rechts.
            HStack(spacing: 12) {
                Button(role: .destructive) {
                    deleteSelected()
                } label: {
                    Label(selected.isEmpty ? "Weggooien" : "Gooi \(selected.count) weg",
                          systemImage: "trash")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(.red)
                .disabled(selected.isEmpty)

                Button { keepSelected() } label: {
                    Label(selected.isEmpty ? "Behouden" : "Behoud \(selected.count)",
                          systemImage: "checkmark")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .tint(.green)
                .disabled(selected.isEmpty)
            }

            HStack {
                Button { toggleSelectAll() } label: {
                    Label(allSelected ? "Deselecteer alles" : "Selecteer alles",
                          systemImage: allSelected ? "circle" : "checkmark.circle")
                }
                Spacer()
                if !selected.isEmpty {
                    Text("\(selected.count) van \(queue.count) geselecteerd")
                        .font(.caption).foregroundStyle(.secondary)
                        .monospacedDigit()
                } else if !lastAction.isEmpty {
                    Button { undoLast() } label: {
                        Label("Ongedaan maken", systemImage: "arrow.uturn.backward")
                            .font(.subheadline)
                    }
                } else {
                    Text("\(queue.count) \(itemNoun)")
                        .font(.caption).foregroundStyle(.secondary)
                        .monospacedDigit()
                }
            }
            .font(.subheadline)
        }
        .padding(.horizontal)
        .padding(.vertical, 10)
        .background(.bar)
    }

    private func toggleSelectAll() {
        Haptics.tap()
        withAnimation {
            if allSelected { selected.removeAll() }
            else { selected = Set(queue.map(\.id)) }
        }
    }

    private var emptyState: some View {
        CenteredEmptyState(
            title: emptyTitle, message: emptyMessage,
            onNext: onNext, nextLabel: nextLabel,
            onUndo: lastAction.isEmpty ? nil : { undoLast() },
            undoLabel: undoLabel
        )
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
            lastAction = toDelete.map { ($0.id, false) }
            selected.removeAll()
        }
    }

    private func keepSelected() {
        Haptics.tap()
        let toKeep = queue.filter { selected.contains($0.id) }
        withAnimation {
            for asset in toKeep { keep.keep(asset.id) }
            lastAction = toKeep.map { ($0.id, true) }
            selected.removeAll()
        }
    }

    /// Korte omschrijving van de laatste actie, bijv. "3 weggegooid".
    private var undoLabel: String {
        let n = lastAction.count
        let kept = lastAction.first?.kept ?? false
        return "\(n) \(kept ? "behouden" : "weggegooid")"
    }

    /// Draait de laatste bulk-actie terug: de items komen weer in de wachtrij.
    private func undoLast() {
        Haptics.tap()
        withAnimation {
            for item in lastAction {
                if item.kept { keep.unkeep(item.id) } else { trash.restore(item.id) }
            }
            lastAction = []
        }
    }
}

/// Gecentreerde "niets te tonen"-weergave: icoon, titel, uitleg en (optioneel)
/// de knop naar de volgende periode — als groep verticaal gecentreerd, zodat de
/// knop midden op het scherm staat en niet strak onderaan.
struct CenteredEmptyState: View {
    let title: String
    let message: String
    var onNext: (() -> Void)? = nil
    var nextLabel = "Volgende met foto's"
    /// Optionele terugdraai-actie (bijv. na een bulk-actie in het raster die de
    /// hele periode leegmaakte), zodat ongedaan maken ook hier bereikbaar blijft.
    var onUndo: (() -> Void)? = nil
    var undoLabel = ""

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "sparkles")
                .font(.system(size: 52))
                .foregroundStyle(.secondary)
            VStack(spacing: 6) {
                Text(title).font(.title2).bold()
                Text(message)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            if let onNext {
                Button {
                    Haptics.tap()
                    onNext()
                } label: {
                    Label(nextLabel, systemImage: "arrow.forward.circle.fill")
                }
                .buttonStyle(.borderedProminent)
                .padding(.top, 4)
            }
            if let onUndo {
                Button { onUndo() } label: {
                    Label(undoLabel.isEmpty ? "Ongedaan maken" : "Ongedaan maken · \(undoLabel)",
                          systemImage: "arrow.uturn.backward")
                }
                .buttonStyle(.bordered)
            }
        }
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
