import SwiftUI

@MainActor
final class OnThisDayViewModel: ObservableObject {
    @Published private(set) var assets: [PhotoAsset] = []
    /// Alleen de allereerste keer tonen we het volledige laadscherm; daarna wordt
    /// nieuwe content geruisloos ingewisseld (geen geknipper).
    @Published private(set) var hasLoaded = false

    private let source: PhotoSource

    init(source: PhotoSource) {
        self.source = source
    }

    func load(for date: Date) async {
        let comps = Calendar.current.dateComponents([.month, .day], from: date)
        let result = await source.fetchPhotos(onMonth: comps.month ?? 1, day: comps.day ?? 1)
        assets = result
        hasLoaded = true
    }

    /// Foto's gegroepeerd per jaar (nieuwste jaar eerst), na uitfilteren van
    /// weggetikte en behouden foto's.
    func groupedByYear(excluding hidden: Set<String>) -> [(year: Int, assets: [PhotoAsset])] {
        let calendar = Calendar.current
        var groups: [Int: [PhotoAsset]] = [:]
        for asset in assets where !hidden.contains(asset.id) {
            guard let date = asset.creationDate else { continue }
            let year = calendar.component(.year, from: date)
            groups[year, default: []].append(asset)
        }
        return groups
            .map { (year: $0.key, assets: $0.value) }
            .sorted { $0.year > $1.year }
    }
}

/// "Op deze dag": foto's van een gekozen datum in eerdere jaren, met per foto de
/// keuze behouden of weggooien. Blader met de dag terug/vooruit of kies een datum.
struct OnThisDayView: View {
    let source: PhotoSource

    @EnvironmentObject private var trash: TrashStore
    @StateObject private var vm: OnThisDayViewModel

    @State private var selectedDate = Date()
    @State private var showDatePicker = false
    /// Foto's die de gebruiker deze sessie bewust heeft behouden.
    @State private var kept: Set<String> = []

    init(source: PhotoSource) {
        self.source = source
        _vm = StateObject(wrappedValue: OnThisDayViewModel(source: source))
    }

    private var hidden: Set<String> {
        trash.trashedIDs.union(kept)
    }

    private var isToday: Bool {
        Calendar.current.isDate(selectedDate, inSameDayAs: Date())
    }

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
            .toolbar {
                if !isToday {
                    Button("Vandaag") { changeDate(to: Date()) }
                }
            }
            .sheet(isPresented: $showDatePicker) {
                datePickerSheet
            }
        }
        .task(id: dateKey) { await vm.load(for: selectedDate) }
        .onReceive(NotificationCenter.default.publisher(for: .photoLibraryDidChange)) { _ in
            Task { await vm.load(for: selectedDate) }
        }
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
            DatePicker(
                "Datum",
                selection: $selectedDate,
                displayedComponents: .date
            )
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

    // MARK: - Inhoud

    @ViewBuilder
    private var content: some View {
        let groups = vm.groupedByYear(excluding: hidden)
        if groups.isEmpty {
            ContentUnavailableView(
                "Niets op deze dag",
                systemImage: "calendar.badge.checkmark",
                description: Text("Geen (resterende) foto's die op \(dayTitle) in eerdere jaren zijn gemaakt.")
            )
        } else {
            VStack(spacing: 0) {
                swipeLegend
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 28) {
                        ForEach(groups, id: \.year) { group in
                            VStack(alignment: .leading, spacing: 12) {
                                Text(yearHeader(group.year))
                                    .font(.title3).bold()
                                    .padding(.horizontal)
                                ForEach(group.assets) { asset in
                                    OnThisDayCard(
                                        asset: asset,
                                        source: source,
                                        onKeep: {
                                            Haptics.tap()
                                            withAnimation { _ = kept.insert(asset.id) }
                                        },
                                        onDiscard: {
                                            Haptics.warning()
                                            withAnimation { trash.mark(asset, reason: "Op deze dag") }
                                        }
                                    )
                                    .padding(.horizontal)
                                }
                            }
                        }
                    }
                    .padding(.top, 4)
                    .padding(.bottom, 8)
                }
                .contentMargins(.bottom, 16, for: .scrollContent)
            }
        }
    }

    /// Vaste legenda die vóór het swipen duidelijk maakt wat links/rechts doet.
    private var swipeLegend: some View {
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
        .padding(.horizontal)
        .padding(.vertical, 8)
    }

    // MARK: - Datumlogica

    /// Sleutel die de `.task` opnieuw laat lopen als de dag verandert.
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
        kept.removeAll()
    }

    private var dayTitle: String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "nl_NL")
        f.dateFormat = "d MMMM"
        return f.string(from: selectedDate)
    }

    private func yearHeader(_ year: Int) -> String {
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

/// Eén foto-kaart met knoppen én swipe-gebaren: sleep naar rechts om te behouden,
/// naar links om weg te gooien.
private struct OnThisDayCard: View {
    let asset: PhotoAsset
    let source: PhotoSource
    var onKeep: () -> Void
    var onDiscard: () -> Void

    @State private var offset: CGFloat = 0
    @State private var removing = false

    private let threshold: CGFloat = 110

    private var keepProgress: Double { Double(min(max(offset / threshold, 0), 1)) }
    private var discardProgress: Double { Double(min(max(-offset / threshold, 0), 1)) }

    var body: some View {
        card
            .offset(x: offset)
            .gesture(dragGesture)
            // VoiceOver-gebruikers kunnen de acties via de rotor uitvoeren.
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Foto. Swipe naar rechts om te behouden, naar links om weg te gooien.")
            .accessibilityAction(named: "Behouden") { onKeep() }
            .accessibilityAction(named: "Weggooien") { onDiscard() }
    }

    /// Vaste hoogte, zodat elke kaart (staand én liggend) even hoog is.
    private let imageHeight: CGFloat = 320

    private var card: some View {
        PhotoThumbnail(asset: asset, source: source, targetSize: CGSize(width: 700, height: 700))
            .frame(maxWidth: .infinity)
            .frame(height: imageHeight)
            .clipped()
            .background(.quaternary)
            .overlay { swipeFeedback }
            .clipShape(RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(.separator.opacity(0.5)))
            .shadow(color: .black.opacity(0.12), radius: 8, y: 3)
    }

    /// Feedback óp de foto tijdens het slepen: groen vinkje = behouden,
    /// rode prullenbak = weggooien.
    @ViewBuilder
    private var swipeFeedback: some View {
        if offset > 0 {
            ZStack {
                Color.green.opacity(keepProgress * 0.35)
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 72))
                    .foregroundStyle(.white)
                    .opacity(keepProgress)
            }
        } else if offset < 0 {
            ZStack {
                Color.red.opacity(discardProgress * 0.35)
                Image(systemName: "trash.circle.fill")
                    .font(.system(size: 72))
                    .foregroundStyle(.white)
                    .opacity(discardProgress)
            }
        }
    }

    private var dragGesture: some Gesture {
        DragGesture(minimumDistance: 12)
            .onChanged { value in
                guard !removing else { return }
                // Alleen horizontaal reageren (verticaal = scrollen).
                if abs(value.translation.width) > abs(value.translation.height) {
                    offset = value.translation.width
                }
            }
            .onEnded { value in
                guard !removing else { return }
                if value.translation.width > threshold {
                    commit(keep: true)
                } else if value.translation.width < -threshold {
                    commit(keep: false)
                } else {
                    withAnimation(.spring) { offset = 0 }
                }
            }
    }

    /// Laat de kaart eerst volledig wegglijden en verwijdert 'm daarna pas uit de
    /// lijst — zo schuift de lijst niet onder je vinger op tijdens het swipen.
    private func commit(keep: Bool) {
        removing = true
        withAnimation(.easeOut(duration: 0.22)) { offset = keep ? 700 : -700 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.22) {
            if keep { onKeep() } else { onDiscard() }
        }
    }
}
