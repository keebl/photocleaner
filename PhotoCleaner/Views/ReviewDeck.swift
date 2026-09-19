import SwiftUI
import AVKit

/// Herbruikbare swipe-stapel: één item tegelijk, swipe rechts = behouden, links
/// = weggooien. Wordt gebruikt voor "Op deze dag", "Random" en "Filmpjes".
struct ReviewDeck: View {
    let assets: [PhotoAsset]
    let source: PhotoSource
    /// Reden waarmee weggegooide items in de prullenbak komen.
    let reason: String
    /// Optionele badge (bijv. jaar of datum) bovenop de kaart.
    var badge: (PhotoAsset) -> String? = { _ in nil }
    var emptyTitle = "Niets te tonen"
    var emptyMessage = "Er zijn hier geen items."
    /// Springt naar de volgende periode (dag/maand/jaar) met onbeoordeelde items.
    /// `nil` = niet beschikbaar (bijv. random-modus, of niets meer te doen).
    var onNext: (() -> Void)? = nil
    var nextLabel = "Volgende met foto's"

    @EnvironmentObject private var trash: TrashStore
    @EnvironmentObject private var keep: KeepStore

    @AppStorage("didSeeSwipeHint") private var didSeeSwipeHint = false
    @State private var history: [(id: String, kept: Bool)] = []
    @State private var freedBytes: Int64 = 0
    @State private var sessionTotal = 0
    @State private var playing: PhotoAsset?
    @State private var inspecting: PhotoAsset?

    private static let deckImageSize = CGSize(width: 1200, height: 1200)

    private var queue: [PhotoAsset] {
        assets.filter { !keep.contains($0.id) && !trash.contains($0.id) }
    }
    private var current: PhotoAsset? { queue.first }

    var body: some View {
        Group {
            if assets.isEmpty {
                emptyStateView
            } else if let current {
                deck(current: current)
            } else {
                doneState
            }
        }
        .fullScreenCover(item: $playing) { asset in
            VideoPlayerScreen(asset: asset, source: source)
        }
        .fullScreenCover(item: $inspecting) { asset in
            PhotoZoomView(asset: asset, source: source)
        }
    }

    private func inspect(_ asset: PhotoAsset) {
        if asset.isVideo { playing = asset } else { inspecting = asset }
    }

    private func deck(current: PhotoAsset) -> some View {
        VStack(spacing: 14) {
            progressBar
            legend

            ZStack {
                if queue.count > 1 {
                    RoundedRectangle(cornerRadius: 22)
                        .fill(.quaternary)
                        .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(.separator.opacity(0.4)))
                        .scaleEffect(0.95)
                        .offset(y: 16)
                }
                DeckCard(
                    asset: current,
                    source: source,
                    badge: badge(current),
                    onKeep: { keepAsset(current) },
                    onDiscard: { discard(current) },
                    onTap: { inspect(current) }
                )
                .id(current.id)

                if !didSeeSwipeHint { swipeCoach }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            undoBar
        }
        .padding(.horizontal)
        .padding(.top, 10)
        .padding(.bottom, 8)
        .onAppear {
            sessionTotal = queue.count
            preloadUpcoming()
        }
        .onChange(of: current.id) { _, _ in preloadUpcoming() }
    }

    /// Eenmalige uitleg over het vegen, over de eerste kaart.
    private var swipeCoach: some View {
        VStack(spacing: 18) {
            Spacer()
            HStack(spacing: 24) {
                VStack(spacing: 6) {
                    Image(systemName: "arrow.left").font(.title)
                    Text("Weggooien").font(.subheadline.weight(.semibold))
                }
                .foregroundStyle(.red)
                VStack(spacing: 6) {
                    Image(systemName: "arrow.right").font(.title)
                    Text("Behouden").font(.subheadline.weight(.semibold))
                }
                .foregroundStyle(.green)
            }
            Label("Tik op de foto om te vergroten", systemImage: "arrow.up.left.and.arrow.down.right")
                .font(.footnote)
                .foregroundStyle(.white)
            Text("Veeg om te kiezen")
                .font(.headline)
                .foregroundStyle(.white)
            Button("Begrepen") { withAnimation { didSeeSwipeHint = true } }
                .buttonStyle(.borderedProminent)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(.black.opacity(0.55))
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .transition(.opacity)
        .accessibilityAddTraits(.isModal)
    }

    private var progressBar: some View {
        // Groeit de wachtrij (nieuwe foto's/undo)? Houd het totaal minstens zo
        // groot, zodat 'decided' nooit negatief of buiten bereik raakt.
        let total = max(sessionTotal, queue.count, 1)
        let decided = min(max(total - queue.count, 0), total)
        return VStack(spacing: 6) {
            Text("\(min(decided + 1, total)) van \(total)")
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.secondary)
            ProgressView(value: Double(decided), total: Double(total))
                .tint(.accentColor)
        }
    }

    private var legend: some View {
        HStack {
            HStack(spacing: 4) { Image(systemName: "arrow.left"); Text("Weggooien") }
                .foregroundStyle(.red)
            Spacer()
            HStack(spacing: 4) { Text("Behouden"); Image(systemName: "arrow.right") }
                .foregroundStyle(.green)
        }
        .font(.caption.weight(.medium))
    }

    @ViewBuilder
    private var undoBar: some View {
        if let last = history.last {
            Button {
                undo()
            } label: {
                Label("Ongedaan maken (\(last.kept ? "behouden" : "weggegooid"))",
                      systemImage: "arrow.uturn.backward")
                    .font(.subheadline)
            }
            .buttonStyle(.bordered)
        }
    }

    private var emptyStateView: some View {
        VStack(spacing: 16) {
            ContentUnavailableView(emptyTitle, systemImage: "sparkles", description: Text(emptyMessage))
            nextButton
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var doneState: some View {
        let kept = history.filter { $0.kept }.count
        let tossed = history.filter { !$0.kept }.count
        return VStack(spacing: 16) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 64))
                .foregroundStyle(.green)
            Text("Klaar")
                .font(.title2).bold()
            Text("\(kept) behouden · \(tossed) weggegooid")
                .foregroundStyle(.secondary)
            if freedBytes > 0 {
                Label("\(ByteFormatter.string(freedBytes)) bespaard", systemImage: "internaldrive")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.green)
            }
            nextButton
            if history.last != nil {
                Button {
                    undo()
                } label: {
                    Label("Laatste ongedaan maken", systemImage: "arrow.uturn.backward")
                }
                .buttonStyle(.bordered)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding()
        .task(id: tossed) { await computeFreedBytes() }
    }

    @ViewBuilder
    private var nextButton: some View {
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

    // MARK: - Beslissingen

    private func keepAsset(_ asset: PhotoAsset) {
        Haptics.tap()
        didSeeSwipeHint = true
        withAnimation(.snappy) {
            keep.keep(asset.id)
            history.append((asset.id, true))
        }
    }

    private func discard(_ asset: PhotoAsset) {
        Haptics.warning()
        didSeeSwipeHint = true
        withAnimation(.snappy) {
            trash.mark(asset, reason: reason)
            history.append((asset.id, false))
        }
    }

    /// Berekent hoeveel opslag de weggegooide items van deze sessie ongeveer
    /// vrijmaken (grootte wordt zo nodig lui bij de bron opgevraagd).
    private func computeFreedBytes() async {
        let tossedIDs = Set(history.filter { !$0.kept }.map(\.id))
        guard !tossedIDs.isEmpty else { freedBytes = 0; return }
        let tossedAssets = assets.filter { tossedIDs.contains($0.id) }
        let sizes = await source.byteSizes(for: tossedAssets)
        freedBytes = sizes.values.reduce(0, +)
    }

    private func undo() {
        guard let last = history.popLast() else { return }
        Haptics.tap()
        withAnimation(.snappy) {
            if last.kept { keep.unkeep(last.id) } else { trash.restore(last.id) }
        }
    }

    private func preloadUpcoming() {
        let upcoming = Array(queue.dropFirst().prefix(3))
        source.preload(upcoming, targetSize: Self.deckImageSize)
    }
}

/// De bovenste kaart in de stapel.
private struct DeckCard: View {
    let asset: PhotoAsset
    let source: PhotoSource
    let badge: String?
    var onKeep: () -> Void
    var onDiscard: () -> Void
    var onTap: () -> Void

    @State private var offset: CGFloat = 0
    @State private var committing = false

    private let threshold: CGFloat = 90

    private var keepProgress: Double { Double(min(max(offset / threshold, 0), 1)) }
    private var discardProgress: Double { Double(min(max(-offset / threshold, 0), 1)) }

    var body: some View {
        card
            .contentShape(Rectangle())
            .offset(x: offset)
            .rotationEffect(.degrees(Double(offset / 22)))
            .gesture(dragGesture)
            .onTapGesture { onTap() }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(accessibilityText)
            .accessibilityAction(named: "Behouden") { onKeep() }
            .accessibilityAction(named: "Weggooien") { onDiscard() }
    }

    private var accessibilityText: String {
        (asset.isVideo ? "Video" : "Foto") + ". Swipe rechts om te behouden, links om weg te gooien."
    }

    private var card: some View {
        PhotoThumbnail(
            asset: asset,
            source: source,
            targetSize: CGSize(width: 1200, height: 1200),
            contentMode: .fit
        )
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.opacity(0.9))
        .overlay(alignment: .top) { badgeView }
        .overlay(alignment: .topTrailing) { shareOverlay }
        .overlay { feedback }
        .overlay { playButton }
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(.separator.opacity(0.5)))
        .shadow(color: .black.opacity(0.18), radius: 12, y: 4)
    }

    @ViewBuilder
    private var badgeView: some View {
        if let badge {
            Text(badge)
                .font(.subheadline.weight(.semibold))
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(.ultraThinMaterial, in: Capsule())
                .padding(.top, 12)
        }
    }

    @ViewBuilder
    private var shareOverlay: some View {
        if offset == 0 {
            ShareButton(asset: asset, source: source)
                .font(.title3)
                .foregroundStyle(.white)
                .padding(8)
                .background(.black.opacity(0.3), in: Circle())
                .padding(10)
        }
    }

    @ViewBuilder
    private var playButton: some View {
        if offset == 0 {
            if asset.isVideo {
                Image(systemName: "play.circle.fill")
                    .font(.system(size: 64))
                    .foregroundStyle(.white, .black.opacity(0.35))
                    .shadow(radius: 6)
            } else {
                // Subtiele hint dat je kunt inzoomen.
                Image(systemName: "arrow.up.left.and.arrow.down.right.circle.fill")
                    .font(.title2)
                    .foregroundStyle(.white, .black.opacity(0.35))
                    .padding(10)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
            }
        }
    }

    @ViewBuilder
    private var feedback: some View {
        if offset > 0 {
            stamp(system: "checkmark.circle.fill", color: .green, opacity: keepProgress)
        } else if offset < 0 {
            stamp(system: "trash.circle.fill", color: .red, opacity: discardProgress)
        }
    }

    private func stamp(system: String, color: Color, opacity: Double) -> some View {
        ZStack {
            color.opacity(opacity * 0.3)
            Image(systemName: system)
                .font(.system(size: 84))
                .foregroundStyle(.white)
                .opacity(opacity)
        }
    }

    private var dragGesture: some Gesture {
        DragGesture(minimumDistance: 10)
            .onChanged { value in
                guard !committing else { return }
                offset = value.translation.width
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
        withAnimation(.easeOut(duration: 0.22)) { offset = keep ? 600 : -600 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            if keep { onKeep() } else { onDiscard() }
        }
    }
}

/// Volledig scherm om een video af te spelen.
struct VideoPlayerScreen: View {
    let asset: PhotoAsset
    let source: PhotoSource

    @Environment(\.dismiss) private var dismiss
    @State private var player: AVPlayer?

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if let player {
                VideoPlayer(player: player).ignoresSafeArea()
            } else {
                ProgressView().tint(.white)
            }
            VStack {
                HStack(spacing: 18) {
                    if let dateText = asset.dateText {
                        Text(dateText)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(.black.opacity(0.4), in: Capsule())
                    }
                    Spacer()
                    ShareButton(asset: asset, source: source)
                        .font(.title2)
                        .foregroundStyle(.white)
                    Button { dismiss() } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title)
                            .foregroundStyle(.white, .black.opacity(0.4))
                    }
                }
                .padding()
                Spacer()
            }
        }
        .task {
            if let item = await source.playerItem(for: asset) {
                let player = AVPlayer(playerItem: item)
                self.player = player
                player.play()
            }
        }
        .onDisappear { player?.pause() }
    }
}
