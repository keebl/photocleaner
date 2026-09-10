import SwiftUI

enum MediaType: String, CaseIterable, Identifiable {
    case opDezeDag
    case random
    case filmpjes
    case dubbelen

    var id: String { rawValue }
    var label: String {
        switch self {
        case .opDezeDag: return "Op deze dag"
        case .random:    return "Random"
        case .filmpjes:  return "Filmpjes"
        case .dubbelen:  return "Dubbelen"
        }
    }
    var icon: String {
        switch self {
        case .opDezeDag: return "calendar"
        case .random:    return "shuffle"
        case .filmpjes:  return "film"
        case .dubbelen:  return "square.on.square"
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

/// Het hoofd-tabblad. Bovenin kies je de bron (iPhone/NAS) en het type
/// (Op deze dag / Random / Filmpjes / Dubbelen).
struct MediaView: View {
    let source: PhotoSource

    @EnvironmentObject private var sources: SourceManager
    @StateObject private var vm: MediaViewModel

    @AppStorage("mediaType") private var selectedTypeRaw = MediaType.opDezeDag.rawValue
    @State private var selectedDate = Date()
    @State private var randomSeed = UUID()
    @State private var showFolderPicker = false

    init(source: PhotoSource) {
        self.source = source
        _vm = StateObject(wrappedValue: MediaViewModel(source: source))
    }

    private var type: MediaType {
        get { MediaType(rawValue: selectedTypeRaw) ?? .opDezeDag }
        nonmutating set { selectedTypeRaw = newValue.rawValue }
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
        .onReceive(NotificationCenter.default.publisher(for: .photoLibraryDidChange)) { _ in
            Task { await vm.load() }
        }
    }

    // MARK: - Kop (bron + type + datum)

    private var header: some View {
        VStack(spacing: 10) {
            HStack {
                sourceMenu
                Spacer()
                if type == .random {
                    Button {
                        randomSeed = UUID()
                    } label: {
                        Label("Nieuwe volgorde", systemImage: "shuffle").labelStyle(.iconOnly)
                    }
                }
            }

            typeChips

            if type == .opDezeDag {
                dateBar
            }
        }
        .padding(.horizontal)
        .padding(.top, 8)
        .padding(.bottom, 6)
    }

    private var sourceMenu: some View {
        Menu {
            Button {
                sources.select(.iphone)
            } label: {
                Label("iPhone-bibliotheek", systemImage: "iphone")
            }
            Button {
                if sources.nasFolderName == nil { showFolderPicker = true } else { sources.select(.nas) }
            } label: {
                Label(sources.nasFolderName ?? "NAS-map kiezen…", systemImage: "externaldrive")
            }
            Button {
                showFolderPicker = true
            } label: {
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

    private var typeChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(MediaType.allCases) { option in
                    Button {
                        type = option
                    } label: {
                        Label(option.label, systemImage: option.icon)
                            .font(.subheadline.weight(.medium))
                            .padding(.horizontal, 12).padding(.vertical, 7)
                            .background(type == option ? Color.accentColor : Color(.secondarySystemBackground),
                                        in: Capsule())
                            .foregroundStyle(type == option ? .white : .primary)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // MARK: - Inhoud per type

    @ViewBuilder
    private var content: some View {
        if !vm.hasLoaded {
            ProgressView("Laden…").frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            switch type {
            case .opDezeDag: opDezeDagDeck
            case .random:    randomDeck
            case .filmpjes:  filmpjesDeck
            case .dubbelen:  DuplicatesView(source: source)
            }
        }
    }

    private var opDezeDagDeck: some View {
        let comps = Calendar.current.dateComponents([.month, .day], from: selectedDate)
        let items = vm.photos.filter {
            guard let d = $0.creationDate else { return false }
            let c = Calendar.current.dateComponents([.month, .day], from: d)
            return c.month == comps.month && c.day == comps.day
        }
        return ReviewDeck(
            assets: items,
            source: source,
            reason: "Op deze dag",
            badge: { yearLabel(for: $0) },
            emptyTitle: "Niets op deze dag",
            emptyMessage: "Geen foto's die op \(dayTitle) in eerdere jaren zijn gemaakt."
        )
        .id("dag-\(dateKey)-\(sources.kind.rawValue)")
    }

    private var randomDeck: some View {
        ReviewDeck(
            assets: shuffled(vm.photos, seed: randomSeed),
            source: source,
            reason: "Random",
            badge: { dateBadge(for: $0) },
            emptyTitle: "Geen foto's",
            emptyMessage: "Er zijn geen foto's in deze bron."
        )
        .id("random-\(randomSeed)-\(sources.kind.rawValue)")
    }

    private var filmpjesDeck: some View {
        ReviewDeck(
            assets: vm.videos,
            source: source,
            reason: "Filmpje",
            badge: { dateBadge(for: $0) },
            emptyTitle: "Geen filmpjes",
            emptyMessage: "Er zijn geen video's in deze bron."
        )
        .id("films-\(sources.kind.rawValue)")
    }

    // MARK: - Datumbalk (alleen Op deze dag)

    private var dateBar: some View {
        HStack {
            Button { shiftDay(-1) } label: {
                Image(systemName: "chevron.left").font(.headline).frame(width: 40, height: 36)
            }
            .accessibilityLabel("Vorige dag")

            Spacer()
            DatePicker("", selection: $selectedDate, displayedComponents: .date)
                .labelsHidden()
            Spacer()

            Button { shiftDay(1) } label: {
                Image(systemName: "chevron.right").font(.headline).frame(width: 40, height: 36)
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
