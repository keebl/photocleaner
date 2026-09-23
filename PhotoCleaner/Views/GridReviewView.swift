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
        GridCell(
            asset: asset,
            source: source,
            height: height,
            thumbSize: Self.thumbSize,
            isSelected: selected.contains(asset.id),
            onTap: { inspect(asset) },
            onToggleSelect: { toggle(asset) },
            onKeep: { keepOne(asset) },
            onDiscard: { discardOne(asset) }
        )
    }

    /// Eén foto behouden (bijv. door 'm in het raster naar rechts te vegen).
    private func keepOne(_ asset: PhotoAsset) {
        withAnimation {
            keep.keep(asset.id)
            selected.remove(asset.id)
            lastAction = [(asset.id, true)]
        }
    }

    /// Eén foto weggooien (bijv. door 'm in het raster naar links te vegen).
    private func discardOne(_ asset: PhotoAsset) {
        withAnimation {
            trash.mark(asset, reason: reason)
            selected.remove(asset.id)
            lastAction = [(asset.id, false)]
        }
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

/// Eén tegel in het raster. Tik = vergroten, rondje = selecteren, en horizontaal
/// vegen = beslissen (rechts behouden, links weggooien) — net als in de stapel.
/// De veeg loopt via een simultane gesture zodat verticaal scrollen blijft werken
/// en reageert alleen op overwegend horizontale bewegingen.
private struct GridCell: View {
    let asset: PhotoAsset
    let source: PhotoSource
    let height: CGFloat
    let thumbSize: CGSize
    let isSelected: Bool
    var onTap: () -> Void
    var onToggleSelect: () -> Void
    var onKeep: () -> Void
    var onDiscard: () -> Void

    @State private var offset: CGFloat = 0
    @State private var committing = false

    private let threshold: CGFloat = 60

    private var keepProgress: Double { Double(min(max(offset / threshold, 0), 1)) }
    private var discardProgress: Double { Double(min(max(-offset / threshold, 0), 1)) }

    var body: some View {
        Color.clear
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .overlay {
                PhotoThumbnail(asset: asset, source: source, targetSize: thumbSize, contentMode: .fill)
            }
            .overlay { swipeFeedback }
            .overlay {
                if isSelected {
                    RoundedRectangle(cornerRadius: 8).fill(Color.accentColor.opacity(0.25))
                    RoundedRectangle(cornerRadius: 8).strokeBorder(Color.accentColor, lineWidth: 3)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 8))
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
            .overlay(alignment: .topTrailing) { selectToggle }
            .contentShape(Rectangle())
            .offset(x: offset)
            .rotationEffect(.degrees(Double(offset / 40)))
            .onTapGesture { onTap() }
            .simultaneousGesture(dragGesture)
            .accessibilityLabel(asset.isVideo ? "Video" : "Foto")
            .accessibilityHint("Tik om te vergroten, veeg om te kiezen, of gebruik het rondje om te selecteren")
            .accessibilityAction(named: "Behouden") { onKeep() }
            .accessibilityAction(named: "Weggooien") { onDiscard() }
    }

    /// Rondje rechtsboven: aan = geselecteerd. Bewust rechtsboven i.p.v. linksboven:
    /// langs de linker schermrand houdt iOS aanrakingen ~2s vast voor het
    /// terug-veeggebaar ("system gesture gate").
    private var selectToggle: some View {
        Button { onToggleSelect() } label: {
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

    @ViewBuilder
    private var swipeFeedback: some View {
        if offset > 0 {
            ZStack {
                Color.green.opacity(keepProgress * 0.35)
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 44))
                    .foregroundStyle(.white)
                    .opacity(keepProgress)
            }
        } else if offset < 0 {
            ZStack {
                Color.red.opacity(discardProgress * 0.35)
                Image(systemName: "trash.circle.fill")
                    .font(.system(size: 44))
                    .foregroundStyle(.white)
                    .opacity(discardProgress)
            }
        }
    }

    private var dragGesture: some Gesture {
        DragGesture(minimumDistance: 12)
            .onChanged { value in
                guard !committing else { return }
                // Alleen reageren op overwegend horizontale bewegingen, zodat een
                // verticale veeg gewoon scrollt.
                if abs(value.translation.width) > abs(value.translation.height) {
                    offset = value.translation.width
                }
            }
            .onEnded { value in
                guard !committing else { return }
                if value.translation.width > threshold {
                    commit(keep: true)
                } else if value.translation.width < -threshold {
                    commit(keep: false)
                } else {
                    withAnimation(.spring) { offset = 0 }
                }
            }
    }

    private func commit(keep: Bool) {
        committing = true
        if keep { Haptics.tap() } else { Haptics.warning() }
        withAnimation(.easeOut(duration: 0.2)) { offset = keep ? 500 : -500 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
            if keep { onKeep() } else { onDiscard() }
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
