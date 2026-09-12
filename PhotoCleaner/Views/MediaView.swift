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

/// Secundaire keuze binnen foto's/filmpjes.
enum BrowseMode: String, CaseIterable, Identifiable {
    case random, opDezeDag
    var id: String { rawValue }
    var label: String {
        switch self {
        case .random:    return "Random"
        case .opDezeDag: return "Op deze dag"
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

/// Het hoofd-tabblad: bron + type bovenin, daaronder de swipe-stapel of dubbelen.
struct MediaView: View {
    let source: PhotoSource

    @EnvironmentObject private var sources: SourceManager
    @EnvironmentObject private var trash: TrashStore
    @StateObject private var vm: MediaViewModel

    @AppStorage("mediaTab") private var tabRaw = MediaTab.photos.rawValue
    @AppStorage("browseMode") private var modeRaw = BrowseMode.opDezeDag.rawValue
    @State private var selectedDate = Date()
    @State private var randomSeed = UUID()
    @State private var showFolderPicker = false

    init(source: PhotoSource) {
        self.source = source
        _vm = StateObject(wrappedValue: MediaViewModel(source: source))
    }

    private var tab: MediaTab {
        get { MediaTab(rawValue: tabRaw) ?? .photos }
        nonmutating set { tabRaw = newValue.rawValue }
    }
    private var mode: BrowseMode {
        get { BrowseMode(rawValue: modeRaw) ?? .opDezeDag }
        nonmutating set { modeRaw = newValue.rawValue }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                header
                Divider()
                content
            }
            .navigationTitle("Media")
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $showFolderPicker) {
                FolderPicker { url in sources.setNASFolder(url) }
                    .ignoresSafeArea()
            }
        }
        .task { await vm.load() }
        .onChange(of: modeRaw) { _, newValue in
            // Elke keer dat je Random opent, een verse volgorde.
            if newValue == BrowseMode.random.rawValue { randomSeed = UUID() }
        }
        .onReceive(NotificationCenter.default.publisher(for: .photoLibraryDidChange)) { _ in
            Task { await vm.load() }
        }
    }

    // MARK: - Kop

    private var header: some View {
        VStack(spacing: 10) {
            HStack {
                sourceMenu
                Spacer()
                Text("\(trash.totalCleaned) opgeschoond")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.secondary)
            }

            Picker("Type", selection: Binding(get: { tab }, set: { tab = $0 })) {
                ForEach(MediaTab.allCases) { Text($0.label).tag($0) }
            }
            .pickerStyle(.segmented)

            if tab != .duplicates {
                Picker("Weergave", selection: Binding(get: { mode }, set: { mode = $0 })) {
                    ForEach(BrowseMode.allCases) { Text($0.label).tag($0) }
                }
                .pickerStyle(.segmented)

                if mode == .opDezeDag { dateBar }
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
            HStack(spacing: 6) {
                Image(systemName: sources.kind.systemImage)
                Text(sources.kind.displayName).fontWeight(.semibold)
                Image(systemName: "chevron.down").font(.caption2)
            }
            .font(.subheadline)
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
        let items = mode == .opDezeDag ? onThisDay(base) : shuffled(base, seed: randomSeed)
        let reason = tab == .videos ? "Filmpje" : (mode == .opDezeDag ? "Op deze dag" : "Random")
        return ReviewDeck(
            assets: items,
            source: source,
            reason: reason,
            badge: { mode == .opDezeDag ? yearLabel(for: $0) : dateBadge(for: $0) },
            emptyTitle: emptyTitle,
            emptyMessage: emptyMessage
        )
        .id("\(tab.rawValue)-\(mode.rawValue)-\(mode == .opDezeDag ? dateKey : randomSeed.uuidString)-\(sources.kind.rawValue)")
    }

    private func onThisDay(_ base: [PhotoAsset]) -> [PhotoAsset] {
        let comps = Calendar.current.dateComponents([.month, .day], from: selectedDate)
        return base.filter {
            guard let d = $0.creationDate else { return false }
            let c = Calendar.current.dateComponents([.month, .day], from: d)
            return c.month == comps.month && c.day == comps.day
        }
    }

    private var emptyTitle: String {
        switch (tab, mode) {
        case (.videos, _):        return "Geen filmpjes"
        case (_, .opDezeDag):     return "Niets op deze dag"
        default:                  return "Geen foto's"
        }
    }

    private var emptyMessage: String {
        switch (tab, mode) {
        case (.videos, .opDezeDag): return "Geen filmpjes van \(dayTitle) in eerdere jaren."
        case (.videos, .random):    return "Er zijn geen video's in deze bron."
        case (_, .opDezeDag):       return "Geen foto's die op \(dayTitle) in eerdere jaren zijn gemaakt."
        default:                    return "Er zijn geen foto's in deze bron."
        }
    }

    // MARK: - Datumbalk

    private var dateBar: some View {
        HStack {
            Button { shiftDay(-1) } label: {
                Image(systemName: "chevron.left").font(.headline).frame(width: 40, height: 34)
            }
            .accessibilityLabel("Vorige dag")

            Spacer()
            DatePicker("", selection: $selectedDate, displayedComponents: .date).labelsHidden()
            Spacer()

            Button { shiftDay(1) } label: {
                Image(systemName: "chevron.right").font(.headline).frame(width: 40, height: 34)
            }
            .accessibilityLabel("Volgende dag")
        }
    }

    // MARK: - Helpers

    private var dateKey: String {
        let c = Calendar.current.dateComponents([.month, .day], from: selectedDate)
        return "\(c.month ?? 0)-\(c.day ?? 0)"
    }

    private func shiftDay(_ days: Int) {
        if let d = Calendar.current.date(byAdding: .day, value: days, to: selectedDate) {
            selectedDate = d
        }
    }

    private var dayTitle: String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "nl_NL")
        f.dateFormat = "d MMMM"
        return f.string(from: selectedDate)
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
