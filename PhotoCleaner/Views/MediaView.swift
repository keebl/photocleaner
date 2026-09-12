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
    @StateObject private var vm: MediaViewModel

    @AppStorage("mediaTab") private var tabRaw = MediaTab.photos.rawValue
    @AppStorage("browse") private var browseRaw = Browse.day.rawValue
    @AppStorage("sortOldFirst") private var sortOldFirst = false
    @State private var selectedDate = Date()
    @State private var randomSeed = UUID()
    @State private var showFolderPicker = false
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
                ToolbarItem(placement: .topBarTrailing) {
                    Text("\(trash.totalCleaned) opgeschoond")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .sheet(isPresented: $showFolderPicker) {
                FolderPicker { url in sources.setNASFolder(url) }
                    .ignoresSafeArea()
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
            Picker("Type", selection: Binding(get: { tab }, set: { tab = $0 })) {
                ForEach(MediaTab.allCases) { Text($0.label).tag($0) }
            }
            .pickerStyle(.segmented)

            if tab != .duplicates {
                Picker("Bladeren", selection: Binding(get: { browse }, set: { browse = $0 })) {
                    ForEach(Browse.allCases) { Text($0.label).tag($0) }
                }
                .pickerStyle(.segmented)

                if !browse.isRandom { periodBar }
            }
        }
        .padding(.horizontal)
        .padding(.top, 8)
        .padding(.bottom, 6)
    }

    private var sourceMenu: some View {
        Menu {
            Button { sources.select(.iphone) } label: {
                Label("iPhone-bibliotheek", systemImage: "iphone")
            }
            Button {
                if sources.nasFolderName == nil { showFolderPicker = true } else { sources.select(.nas) }
            } label: {
                Label(sources.nasFolderName ?? "NAS-map kiezen…", systemImage: "externaldrive")
            }
            Button { showFolderPicker = true } label: {
                Label("Andere NAS-map…", systemImage: "folder.badge.plus")
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
                Text(periodTitle).font(.headline).frame(maxWidth: .infinity)
            }
            .buttonStyle(.plain)

            Button { shiftPeriod(1) } label: {
                Image(systemName: "chevron.right").font(.headline).frame(width: 40, height: 34)
            }
            .accessibilityLabel("Volgende")

            Button { sortOldFirst.toggle() } label: {
                Image(systemName: sortOldFirst ? "arrow.up" : "arrow.down")
                    .frame(width: 34, height: 34)
            }
            .buttonStyle(.bordered)
            .accessibilityLabel(sortOldFirst ? "Oudste eerst" : "Nieuwste eerst")
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
            deck
        }
    }

    private var deck: some View {
        let base = tab == .videos ? vm.videos : vm.photos
        let items = browse.isRandom ? shuffled(base, seed: randomSeed) : filtered(base)
        let reason = tab == .videos ? "Filmpje" : (browse.isRandom ? "Random" : "Op deze dag")
        return ReviewDeck(
            assets: items,
            source: source,
            reason: reason,
            badge: { badge(for: $0) },
            emptyTitle: emptyTitle,
            emptyMessage: emptyMessage
        )
        .id("\(tab.rawValue)-\(browse.rawValue)-\(deckKey)-\(sortOldFirst)-\(sources.kind.rawValue)")
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
