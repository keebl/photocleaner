import SwiftUI

/// Primaire keuze: wat voor media (of duplicaten).
enum MediaTab: String, CaseIterable, Identifiable {
    case photos, videos, duplicates
    var id: String { rawValue }
    var label: String {
        switch self {
        case .photos:     return "Foto's"
        case .videos:     return "Filmpjes"
        case .duplicates: return "Dubbelen"
        }
    }
    var systemImage: String {
        switch self {
        case .photos:     return "photo"
        case .videos:     return "video"
        case .duplicates: return "square.on.square"
        }
    }
}

/// Hoe je door de foto's/filmpjes bladert. Random, of "op deze dag" verbreed naar
/// dag/maand/jaar — in één keuze.
enum Browse: String, CaseIterable, Identifiable {
    case random, day, month, year
    var id: String { rawValue }
    var label: String {
        switch self {
        case .random: return "Random"
        case .day:    return "Dag"
        case .month:  return "Maand"
        case .year:   return "Jaar"
        }
    }
    var isRandom: Bool { self == .random }
    var systemImage: String {
        switch self {
        case .random: return "shuffle"
        case .day:    return "calendar"
        case .month:  return "calendar"
        case .year:   return "calendar"
        }
    }
    var component: Calendar.Component {
        switch self {
        case .day, .random: return .day
        case .month:        return .month
        case .year:         return .year
        }
    }
}

@MainActor
final class MediaViewModel: ObservableObject {
    @Published private(set) var assets: [PhotoAsset] = []
    @Published private(set) var hasLoaded = false

    let source: PhotoSource
    init(source: PhotoSource) { self.source = source }

    func load() async {
        assets = await source.fetchAllPhotos()
        hasLoaded = true
    }

    var photos: [PhotoAsset] { assets.filter { $0.kind == .photo } }
    var videos: [PhotoAsset] { assets.filter { $0.kind == .video } }
}

/// Het hoofd-tabblad. Bron + teller staan in de navigatiebalk; daaronder een
/// compacte kop: type, bladerkeuze en (bij dag/maand/jaar) de periode.
struct MediaView: View {
    let source: PhotoSource

    @EnvironmentObject private var sources: SourceManager
    @EnvironmentObject private var trash: TrashStore
    @EnvironmentObject private var keep: KeepStore
    @StateObject private var vm: MediaViewModel

    @AppStorage("mediaTab") private var tabRaw = MediaTab.photos.rawValue
    @AppStorage("browse") private var browseRaw = Browse.day.rawValue
    @AppStorage("sortOldFirst") private var sortOldFirst = false
    @AppStorage("selectedTab") private var selectedTab = 0
    /// Bewust géén @AppStorage: elke start begint met de enkele-foto-weergave;
    /// het raster is een keuze binnen de sessie.
    @State private var gridMode = false
    @State private var selectedDate = Date()
    @State private var randomSeed = UUID()
    @State private var showSMBConnect = false
    @State private var showDatePicker = false

    init(source: PhotoSource) {
        self.source = source
        _vm = StateObject(wrappedValue: MediaViewModel(source: source))
    }

    private var tab: MediaTab {
        get { MediaTab(rawValue: tabRaw) ?? .photos }
        nonmutating set { tabRaw = newValue.rawValue }
    }
    private var browse: Browse {
        get { Browse(rawValue: browseRaw) ?? .day }
        nonmutating set { browseRaw = newValue.rawValue }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                header
                Divider()
                content
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { sourceMenu }
                if tab != .duplicates {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            Haptics.tap()
                            gridMode.toggle()
                        } label: {
                            Image(systemName: gridMode ? "rectangle.portrait" : "square.grid.2x2")
                        }
                        .accessibilityLabel(gridMode ? "Toon als veegstapel" : "Toon als raster")
                    }
                }
                if trash.totalCleaned > 0 {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            Haptics.tap()
                            selectedTab = 1   // naar de Prullenbak
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "trash")
                                Text("\(trash.totalCleaned)").monospacedDigit()
                            }
                            .font(.subheadline)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                        }
                        .foregroundStyle(.secondary)
                        .accessibilityLabel("\(trash.totalCleaned) opgeschoond, open prullenbak")
                    }
                }
            }
            .sheet(isPresented: $showSMBConnect) {
                SMBConnectView(source: sources.smb) { sources.activateSMB() }
            }
            .sheet(isPresented: $showDatePicker) {
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
        }
        .task { await vm.load() }
        .onChange(of: browseRaw) { _, newValue in
            if newValue == Browse.random.rawValue { randomSeed = UUID() }
        }
        .onReceive(NotificationCenter.default.publisher(for: .photoLibraryDidChange)) { _ in
            Task { await vm.load() }
        }
    }

    // MARK: - Kop

    private var header: some View {
        VStack(spacing: 10) {
            HStack(spacing: 10) {
                typeMenu
                if tab != .duplicates { browseMenu }
                Spacer(minLength: 0)
            }

            if tab != .duplicates && !browse.isRandom { periodBar }
        }
        .padding(.horizontal)
        .padding(.top, 8)
        .padding(.bottom, 6)
    }

    private var typeMenu: some View {
        Menu {
            Picker("Type", selection: Binding(get: { tab }, set: { tab = $0 })) {
                ForEach(MediaTab.allCases) { Label($0.label, systemImage: $0.systemImage).tag($0) }
            }
        } label: {
            dropdownLabel(icon: tab.systemImage, text: tab.label)
        }
    }

    private var browseMenu: some View {
        Menu {
            Picker("Bladeren", selection: Binding(get: { browse }, set: { browse = $0 })) {
                ForEach(Browse.allCases) { Label($0.label, systemImage: $0.systemImage).tag($0) }
            }
        } label: {
            dropdownLabel(icon: browse.systemImage, text: browse.label)
        }
    }

    private func dropdownLabel(icon: String, text: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
            Text(text).fontWeight(.medium)
            Image(systemName: "chevron.down").font(.caption2).foregroundStyle(.secondary)
        }
        .font(.subheadline)
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(.quaternary, in: Capsule())
    }

    private var sourceMenu: some View {
        Menu {
            Button { sources.select(.iphone) } label: {
                Label("iPhone-bibliotheek", systemImage: "iphone")
            }
            if sources.hasSMB {
                Button { sources.select(.nas) } label: {
                    Label(sources.smbName ?? "NAS (SMB)", systemImage: "externaldrive.connected.to.line.below")
                }
                Button { showSMBConnect = true } label: {
                    Label("NAS-koppeling wijzigen…", systemImage: "gearshape")
                }
            } else {
                Button { showSMBConnect = true } label: {
                    Label("NAS koppelen…", systemImage: "externaldrive.badge.plus")
                }
            }
        } label: {
            HStack(spacing: 5) {
                Image(systemName: sources.kind.systemImage)
                Text(sources.kind.displayName).fontWeight(.semibold).lineLimit(1)
                Image(systemName: "chevron.down").font(.caption2)
            }
            .font(.subheadline)
        }
    }

    private var periodBar: some View {
        HStack(spacing: 6) {
            Button { shiftPeriod(-1) } label: {
                Image(systemName: "chevron.left").font(.headline).frame(width: 40, height: 34)
            }
            .accessibilityLabel("Vorige")

            Button { showDatePicker = true } label: {
                HStack(spacing: 5) {
                    Image(systemName: "calendar").font(.subheadline)
                    Text(periodTitle).font(.headline)
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.plain)
            .accessibilityHint("Kies een datum")

            Button { shiftPeriod(1) } label: {
                Image(systemName: "chevron.right").font(.headline).frame(width: 40, height: 34)
            }
            .accessibilityLabel("Volgende")

            Menu {
                Picker("Volgorde", selection: $sortOldFirst) {
                    Label("Nieuwste eerst", systemImage: "arrow.down").tag(false)
                    Label("Oudste eerst", systemImage: "arrow.up").tag(true)
                }
            } label: {
                Image(systemName: "arrow.up.arrow.down")
                    .frame(width: 34, height: 34)
            }
            .buttonStyle(.bordered)
            .accessibilityLabel("Sorteervolgorde")
        }
    }

    // MARK: - Inhoud

    @ViewBuilder
    private var content: some View {
        if !vm.hasLoaded {
            ProgressView("Laden…").frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if tab == .duplicates {
            DuplicatesView(source: source)
        } else {
            reviewContent
        }
    }

    @ViewBuilder
    private var reviewContent: some View {
        let base = tab == .videos ? vm.videos : vm.photos
        let items = browse.isRandom ? shuffled(base, seed: randomSeed) : filtered(base)
        let reason = tab == .videos ? "Filmpje" : (browse.isRandom ? "Random" : "Op deze dag")
        // Alleen bij dag/maand/jaar: spring naar de volgende periode met nog te
        // beoordelen items.
        let nextDate = browse.isRandom ? nil : nextPeriodDate(in: base)
        let onNext: (() -> Void)? = nextDate.map { date in { withAnimation { selectedDate = date } } }

        Group {
            if gridMode {
                GridReviewView(
                    assets: items, source: source, reason: reason,
                    emptyTitle: emptyTitle, emptyMessage: emptyMessage,
                    onNext: onNext, nextLabel: nextLabel
                )
            } else {
                ReviewDeck(
                    assets: items, source: source, reason: reason,
                    badge: { badge(for: $0) },
                    emptyTitle: emptyTitle, emptyMessage: emptyMessage,
                    onNext: onNext, nextLabel: nextLabel
                )
            }
        }
        .id("\(tab.rawValue)-\(browse.rawValue)-\(deckKey)-\(sortOldFirst)-\(sources.kind.rawValue)-\(gridMode)")
    }

    private var nextLabel: String {
        switch browse {
        case .day:   return "Volgende dag met foto's"
        case .month: return "Volgende maand met foto's"
        case .year:  return "Volgend jaar met foto's"
        case .random: return "Volgende"
        }
    }

    /// Zoekt de eerstvolgende periode (dag/maand/jaar) ná de huidige die nog
    /// onbeoordeelde items heeft. Wrapt rond de kalender/jaren; `nil` als er niets
    /// meer te doen is (of de huidige periode de enige is).
    private func nextPeriodDate(in base: [PhotoAsset]) -> Date? {
        let cal = Calendar.current
        let undecided = base.filter { !keep.contains($0.id) && !trash.contains($0.id) && $0.creationDate != nil }
        guard !undecided.isEmpty else { return nil }

        switch browse {
        case .day:
            let keys = Set(undecided.compactMap { asset -> Int? in
                let c = cal.dateComponents([.month, .day], from: asset.creationDate!)
                guard let m = c.month, let d = c.day else { return nil }
                return m * 100 + d
            })
            var probe = selectedDate
            for _ in 1...366 {
                guard let next = cal.date(byAdding: .day, value: 1, to: probe) else { break }
                probe = next
                let c = cal.dateComponents([.month, .day], from: probe)
                if let m = c.month, let d = c.day, keys.contains(m * 100 + d) { return probe }
            }
        case .month:
            let months = Set(undecided.compactMap { cal.dateComponents([.month], from: $0.creationDate!).month })
            var probe = selectedDate
            for _ in 1...12 {
                guard let next = cal.date(byAdding: .month, value: 1, to: probe) else { break }
                probe = next
                if let m = cal.dateComponents([.month], from: probe).month, months.contains(m) { return probe }
            }
        case .year:
            let years = Set(undecided.compactMap { cal.dateComponents([.year], from: $0.creationDate!).year })
            let current = cal.component(.year, from: selectedDate)
            // eerst een later jaar; anders rond naar het vroegste jaar
            guard let target = years.filter({ $0 > current }).min() ?? years.filter({ $0 < current }).min()
            else { return nil }
            var comps = cal.dateComponents([.month, .day], from: selectedDate)
            comps.year = target
            return cal.date(from: comps)
        case .random:
            return nil
        }
        return nil
    }

    private var deckKey: String {
        browse.isRandom ? randomSeed.uuidString : periodKey
    }

    /// Filtert op het gekozen bereik (dag/maand/jaar) en sorteert op richting.
    private func filtered(_ base: [PhotoAsset]) -> [PhotoAsset] {
        let cal = Calendar.current
        let ref = cal.dateComponents([.year, .month, .day], from: selectedDate)
        let result = base.filter { asset in
            guard let d = asset.creationDate else { return false }
            let c = cal.dateComponents([.year, .month, .day], from: d)
            switch browse {
            case .day:   return c.month == ref.month && c.day == ref.day
            case .month: return c.month == ref.month
            case .year:  return c.year == ref.year
            case .random: return true
            }
        }
        return result.sorted { a, b in
            let da = a.creationDate ?? .distantPast
            let db = b.creationDate ?? .distantPast
            return sortOldFirst ? da < db : da > db
        }
    }

    private func badge(for asset: PhotoAsset) -> String? {
        switch browse {
        case .random, .year: return dateBadge(for: asset)
        case .day, .month:   return yearLabel(for: asset)
        }
    }

    private var emptyTitle: String {
        if tab == .videos { return "Geen filmpjes" }
        return browse.isRandom ? "Geen foto's" : "Niets gevonden"
    }

    private var emptyMessage: String {
        let kind = tab == .videos ? "filmpjes" : "foto's"
        if browse.isRandom { return "Er zijn geen \(kind) in deze bron." }
        return "Geen \(kind) voor \(periodTitle)."
    }

    // MARK: - Periode-helpers

    private var periodKey: String {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: selectedDate)
        switch browse {
        case .day:            return "\(c.month ?? 0)-\(c.day ?? 0)"
        case .month:          return "m\(c.month ?? 0)"
        case .year:           return "y\(c.year ?? 0)"
        case .random:         return "random"
        }
    }

    private func shiftPeriod(_ delta: Int) {
        if let d = Calendar.current.date(byAdding: browse.component, value: delta, to: selectedDate) {
            selectedDate = d
        }
    }

    private var periodTitle: String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "nl_NL")
        switch browse {
        case .day:
            f.dateFormat = "d MMMM"
            return f.string(from: selectedDate)
        case .month:
            f.dateFormat = "LLLL"
            let s = f.string(from: selectedDate)
            return s.prefix(1).uppercased() + s.dropFirst()
        case .year:
            f.dateFormat = "yyyy"
            return f.string(from: selectedDate)
        case .random:
            return ""
        }
    }

    private func dateBadge(for asset: PhotoAsset) -> String? {
        guard let date = asset.creationDate else { return nil }
        let f = DateFormatter()
        f.locale = Locale(identifier: "nl_NL")
        f.dateFormat = "d MMM yyyy"
        return f.string(from: date)
    }

    private func yearLabel(for asset: PhotoAsset) -> String? {
        guard let date = asset.creationDate else { return nil }
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

    private func shuffled(_ array: [PhotoAsset], seed: UUID) -> [PhotoAsset] {
        var generator = SeededGenerator(seed: UInt64(bitPattern: Int64(seed.hashValue)))
        return array.shuffled(using: &generator)
    }
}

/// Deterministische generator, zodat een 'random' volgorde stabiel blijft tot je
/// bewust een nieuwe volgorde kiest.
struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64
    init(seed: UInt64) { state = seed == 0 ? 0x9E3779B97F4A7C15 : seed }
    mutating func next() -> UInt64 {
        state ^= state << 13
        state ^= state >> 7
        state ^= state << 17
        return state
    }
}
