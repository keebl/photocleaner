import SwiftUI

@MainActor
final class OnThisDayViewModel: ObservableObject {
    @Published private(set) var assets: [PhotoAsset] = []
    /// Alleen de allereerste keer tonen we het volledige laadscherm; daarna wordt
    /// nieuwe content geruisloos ingewisseld.
    @Published private(set) var hasLoaded = false

    private let source: PhotoSource

    init(source: PhotoSource) {
        self.source = source
    }

    func load(for date: Date) async {
        let comps = Calendar.current.dateComponents([.month, .day], from: date)
        assets = await source.fetchPhotos(onMonth: comps.month ?? 1, day: comps.day ?? 1)
        hasLoaded = true
    }
}

/// "Op deze dag" als swipe-stapel: één foto tegelijk groot in beeld. Swipe naar
/// rechts om te behouden, naar links om weg te gooien; de volgende foto verschijnt.
/// Geen scrollen, dus geen gebaren-conflict en altijd de juiste foto.
struct OnThisDayView: View {
    let source: PhotoSource

    @EnvironmentObject private var trash: TrashStore
    @StateObject private var vm: OnThisDayViewModel

    @State private var selectedDate = Date()
    @State private var showDatePicker = false

    /// Deze sessie behouden foto's (per dag gereset).
    @State private var keptIDs: Set<String> = []
    /// Volgorde van beslissingen, voor "ongedaan maken".
    @State private var history: [(id: String, kept: Bool)] = []

    init(source: PhotoSource) {
        self.source = source
        _vm = StateObject(wrappedValue: OnThisDayViewModel(source: source))
    }

    private var isToday: Bool {
        Calendar.current.isDate(selectedDate, inSameDayAs: Date())
    }

    /// Nog te beoordelen foto's (niet behouden en niet in de prullenbak).
    private var queue: [PhotoAsset] {
        vm.assets.filter { !keptIDs.contains($0.id) && !trash.contains($0.id) }
    }

    private var current: PhotoAsset? { queue.first }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                dateBar
                Divider()
                Group {
                    if !vm.hasLoaded {
                        ProgressView("Foto's laden…")
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else {
                        content
                    }
                }
            }
            .navigationTitle("Op deze dag")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if !isToday {
                    Button("Vandaag") { changeDate(to: Date()) }
                }
            }
            .sheet(isPresented: $showDatePicker) { datePickerSheet }
        }
        .task(id: dateKey) {
            resetSession()
            await vm.load(for: selectedDate)
        }
        .onReceive(NotificationCenter.default.publisher(for: .photoLibraryDidChange)) { _ in
            Task { await vm.load(for: selectedDate) }
        }
    }

    // MARK: - Inhoud

    @ViewBuilder
    private var content: some View {
        if vm.assets.isEmpty {
            emptyState
        } else if let current {
            deck(current: current)
        } else {
            doneState
        }
    }

    private func deck(current: PhotoAsset) -> some View {
        VStack(spacing: 14) {
            progressBar
            legend

            DeckCard(
                asset: current,
                source: source,
                yearLabel: yearLabel(for: current),
                onKeep: { keep(current) },
                onDiscard: { discard(current) }
            )
            .id(current.id)   // nieuwe kaart = schone staat
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            undoBar
        }
        .padding(.horizontal)
        .padding(.top, 10)
        .padding(.bottom, 8)
    }

    private var progressBar: some View {
        let total = vm.assets.count
        let decided = total - queue.count
        return VStack(spacing: 6) {
            Text("Foto \(min(decided + 1, total)) van \(total)")
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.secondary)
            ProgressView(value: Double(decided), total: Double(max(total, 1)))
                .tint(.accentColor)
        }
    }

    private var legend: some View {
        HStack {
            HStack(spacing: 4) {
                Image(systemName: "arrow.left")
                Text("Weggooien")
            }
            .foregroundStyle(.red)
            Spacer()
            HStack(spacing: 4) {
                Text("Behouden")
                Image(systemName: "arrow.right")
            }
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

    private var doneState: some View {
        let kept = history.filter { $0.kept }.count
        let tossed = history.filter { !$0.kept }.count
        return VStack(spacing: 16) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 64))
                .foregroundStyle(.green)
            Text("Klaar met \(dayTitle)")
                .font(.title2).bold()
            Text("\(kept) behouden · \(tossed) weggegooid")
                .foregroundStyle(.secondary)
            if history.last != nil {
                Button {
                    undo()
                } label: {
                    Label("Laatste ongedaan maken", systemImage: "arrow.uturn.backward")
                }
                .buttonStyle(.bordered)
            }
            Button {
                changeDate(to: Calendar.current.date(byAdding: .day, value: 1, to: selectedDate) ?? selectedDate)
            } label: {
                Label("Volgende dag", systemImage: "chevron.right")
            }
            .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding()
    }

    private var emptyState: some View {
        ContentUnavailableView(
            "Niets op deze dag",
            systemImage: "calendar.badge.checkmark",
            description: Text("Geen foto's die op \(dayTitle) in eerdere jaren zijn gemaakt.")
        )
    }

    // MARK: - Beslissingen

    private func keep(_ asset: PhotoAsset) {
        Haptics.tap()
        withAnimation(.snappy) {
            keptIDs.insert(asset.id)
            history.append((asset.id, true))
        }
    }

    private func discard(_ asset: PhotoAsset) {
        Haptics.warning()
        withAnimation(.snappy) {
            trash.mark(asset, reason: "Op deze dag")
            history.append((asset.id, false))
        }
    }

    private func undo() {
        guard let last = history.popLast() else { return }
        Haptics.tap()
        withAnimation(.snappy) {
            if last.kept {
                keptIDs.remove(last.id)
            } else {
                trash.restore(last.id)
            }
        }
    }

    private func resetSession() {
        keptIDs.removeAll()
        history.removeAll()
    }

    // MARK: - Datumbalk

    private var dateBar: some View {
        HStack {
            Button { shiftDay(-1) } label: {
                Image(systemName: "chevron.left").font(.title3.weight(.semibold))
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel("Vorige dag")

            Spacer()

            Button { showDatePicker = true } label: {
                VStack(spacing: 2) {
                    Text(dayTitle).font(.headline)
                    Text(isToday ? "Vandaag" : "Kies datum")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Datum: \(dayTitle). Tik om een datum te kiezen.")

            Spacer()

            Button { shiftDay(1) } label: {
                Image(systemName: "chevron.right").font(.title3.weight(.semibold))
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel("Volgende dag")
        }
        .padding(.horizontal)
        .padding(.vertical, 6)
    }

    private var datePickerSheet: some View {
        NavigationStack {
            DatePicker("Datum", selection: $selectedDate, displayedComponents: .date)
                .datePickerStyle(.graphical)
                .padding()
                .navigationTitle("Kies een datum")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Klaar") { showDatePicker = false }
                    }
                }
        }
        .presentationDetents([.medium, .large])
    }

    // MARK: - Datumlogica

    private var dateKey: String {
        let c = Calendar.current.dateComponents([.month, .day], from: selectedDate)
        return "\(c.month ?? 0)-\(c.day ?? 0)"
    }

    private func shiftDay(_ days: Int) {
        if let d = Calendar.current.date(byAdding: .day, value: days, to: selectedDate) {
            changeDate(to: d)
        }
    }

    private func changeDate(to date: Date) {
        selectedDate = date
    }

    private var dayTitle: String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "nl_NL")
        f.dateFormat = "d MMMM"
        return f.string(from: selectedDate)
    }

    private func yearLabel(for asset: PhotoAsset) -> String {
        guard let date = asset.creationDate else { return "" }
        let year = Calendar.current.component(.year, from: date)
        let reference = Calendar.current.component(.year, from: selectedDate)
        let ago = reference - year
        switch ago {
        case ..<0:  return "\(year)"
        case 0:     return "Dit jaar"
        case 1:     return "1 jaar geleden · \(year)"
        default:    return "\(ago) jaar geleden · \(year)"
        }
    }
}

/// De bovenste kaart in de stapel: één foto, swipebaar. Omdat er telkens maar
/// één interactieve kaart is (en geen scrollview), is er nooit twijfel welke foto
/// je swipet.
private struct DeckCard: View {
    let asset: PhotoAsset
    let source: PhotoSource
    let yearLabel: String
    var onKeep: () -> Void
    var onDiscard: () -> Void

    @State private var offset: CGFloat = 0
    @State private var committing = false

    private let threshold: CGFloat = 90

    private var keepProgress: Double { Double(min(max(offset / threshold, 0), 1)) }
    private var discardProgress: Double { Double(min(max(-offset / threshold, 0), 1)) }

    var body: some View {
        photo
            .overlay(alignment: .top) { yearBadge }
            .overlay { feedback }
            .clipShape(RoundedRectangle(cornerRadius: 22))
            .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(.separator.opacity(0.5)))
            .shadow(color: .black.opacity(0.18), radius: 12, y: 4)
            .offset(x: offset)
            .rotationEffect(.degrees(Double(offset / 22)))
            .gesture(dragGesture)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Foto, \(yearLabel). Swipe rechts om te behouden, links om weg te gooien.")
            .accessibilityAction(named: "Behouden") { onKeep() }
            .accessibilityAction(named: "Weggooien") { onDiscard() }
    }

    private var photo: some View {
        PhotoThumbnail(
            asset: asset,
            source: source,
            targetSize: CGSize(width: 1200, height: 1200),
            contentMode: .fit
        )
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.opacity(0.9))
    }

    private var yearBadge: some View {
        Text(yearLabel)
            .font(.subheadline.weight(.semibold))
            .padding(.horizontal, 12).padding(.vertical, 6)
            .background(.ultraThinMaterial, in: Capsule())
            .padding(.top, 12)
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
