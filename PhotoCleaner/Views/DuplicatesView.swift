import SwiftUI

@MainActor
final class DuplicatesViewModel: ObservableObject {
    enum Mode: String, CaseIterable, Identifiable {
        case exact = "Exact"
        case similar = "Lijkend"
        var id: String { rawValue }
    }

    @Published var mode: Mode = ProcessInfo.processInfo.environment["START_DUP_MODE"] == "similar" ? .similar : .exact
    @Published private(set) var exactGroups: [DuplicateGroup] = []
    @Published private(set) var similarGroups: [DuplicateGroup] = []
    @Published private(set) var isLoading = false          // eerste (exacte) scan
    @Published private(set) var hasScanned = false          // eenmaal geladen? dan niet meer blanken
    @Published private(set) var isRefreshing = false        // handmatige 'opnieuw scannen'
    @Published private(set) var isScanningSimilar = false   // zwaardere perceptuele scan
    @Published private(set) var scanProgress: Double = 0

    private let source: PhotoSource
    private var visible: [PhotoAsset] = []
    private var similarComputed = false
    private var similarTask: Task<Void, Never>?

    init(source: PhotoSource) {
        self.source = source
    }

    var groups: [DuplicateGroup] {
        mode == .exact ? exactGroups : similarGroups
    }

    /// Snelle basisscan: haalt de bibliotheek op (gecached) en zoekt exacte
    /// dubbelen. De perceptuele scan draait pas op aanvraag.
    func scan(excluding hidden: Set<String>) async {
        cancelSimilar()
        similarComputed = false
        similarGroups = []

        isLoading = true
        defer { isLoading = false }

        let all = await source.fetchAllPhotos()
        // Exacte dubbelen: foto's én video's (identieke bestanden).
        visible = all.filter { !hidden.contains($0.id) }
        exactGroups = await refineExact(DuplicateDetector.findDuplicates(in: visible))
        hasScanned = true
    }

    /// Verscherpt de exacte-duplicaatkandidaten (zelfde opnametijd + afmeting) door
    /// binnen elke kandidaatgroep óók op bestandsgrootte te groeperen. Zo vallen
    /// bijv. burst-foto's (zelfde seconde/afmeting, andere inhoud → andere grootte)
    /// af, en blijven alleen écht identieke bestanden over.
    private func refineExact(_ candidates: [DuplicateGroup]) async -> [DuplicateGroup] {
        let ids = candidates.flatMap { $0.all }
        guard !ids.isEmpty else { return [] }
        let sizes = await source.byteSizes(for: ids)

        var refined: [DuplicateGroup] = []
        for group in candidates {
            let sized = group.all.map { $0.withByteSize(sizes[$0.id] ?? 0) }
            let bySize = Dictionary(grouping: sized, by: { $0.byteSize })
            for (size, sameSize) in bySize where size > 0 && sameSize.count > 1 {
                refined.append(DuplicateGroup.make(from: sameSize))
            }
        }
        return refined.sorted { $0.reclaimableBytes > $1.reclaimableBytes }
    }

    /// Handmatige verversing: cache weggooien en opnieuw scannen.
    func refresh(excluding hidden: Set<String>) async {
        isRefreshing = true
        defer { isRefreshing = false }
        source.invalidateCache()
        await scan(excluding: hidden)
        if mode == .similar { ensureSimilarLoaded() }
    }

    /// Start (indien nodig) de perceptuele scan als annuleerbare achtergrondtaak.
    func ensureSimilarLoaded() {
        guard !similarComputed, similarTask == nil else { return }
        similarTask = Task { [weak self] in
            await self?.computeSimilar()
            self?.similarTask = nil
        }
    }

    func cancelSimilar() {
        similarTask?.cancel()
        similarTask = nil
        isScanningSimilar = false
    }

    /// Haalt een afgehandelde groep uit beide lijsten (na "gooi weg").
    func remove(_ group: DuplicateGroup) {
        exactGroups.removeAll { $0.id == group.id }
        similarGroups.removeAll { $0.id == group.id }
    }

    private func computeSimilar() async {
        isScanningSimilar = true
        scanProgress = 0
        defer { isScanningSimilar = false }

        // Lijkende foto's alleen op foto's; posterframe-hashes van video's zijn onbetrouwbaar.
        let toScan = visible.filter { $0.kind == .photo }
        let total = max(toScan.count, 1)
        var hashed: [(asset: PhotoAsset, hash: UInt64)] = []
        hashed.reserveCapacity(toScan.count)

        for (index, asset) in toScan.enumerated() {
            if Task.isCancelled { return }
            if let hash = await source.perceptualHash(for: asset) {
                hashed.append((asset, hash))
            }
            // Regelmatig even ademruimte geven (voorkomt oplopende warmte/UI-lag).
            if index % 25 == 0 {
                scanProgress = Double(index) / Double(total)
                await Task.yield()
            }
        }

        if Task.isCancelled { return }
        scanProgress = 1
        similarGroups = await enrich(PerceptualDetector.group(hashed, maxDistance: 8))
        similarComputed = true
        source.flushCaches()   // hashes bewaren voor een supersnelle volgende scan
    }

    /// Vult de bestandsgrootte aan voor álléén de gegroepeerde foto's (weinig) en
    /// herbouwt de groepen zodat "te winnen" en de beste-keuze kloppen.
    private func enrich(_ groups: [DuplicateGroup]) async -> [DuplicateGroup] {
        let assets = groups.flatMap { $0.all }
        guard !assets.isEmpty else { return groups }
        let sizes = await source.byteSizes(for: assets)
        return groups
            .map { group in
                DuplicateGroup.make(from: group.all.map { $0.withByteSize(sizes[$0.id] ?? 0) })
            }
            .sorted { $0.reclaimableBytes > $1.reclaimableBytes }
    }
}

/// "Dubbelen": exacte (metadata) én lijkende (perceptual) duplicaten. Per groep
/// stellen we voor de beste te behouden en de rest weg te gooien.
struct DuplicatesView: View {
    let source: PhotoSource

    @EnvironmentObject private var trash: TrashStore
    @StateObject private var vm: DuplicatesViewModel

    @State private var inspecting: PhotoAsset?
    @State private var playing: PhotoAsset?
    /// Aantal groepen dat we tonen; in batches uitbreidbaar zodat niet alles
    /// tegelijk hoeft te laden (previews van de NAS zijn relatief zwaar).
    @State private var visibleCount = Self.batchSize
    private static let batchSize = 10

    init(source: PhotoSource) {
        self.source = source
        _vm = StateObject(wrappedValue: DuplicatesViewModel(source: source))
    }

    private func inspect(_ asset: PhotoAsset) {
        if asset.isVideo { playing = asset } else { inspecting = asset }
    }

    private func resolve(_ group: DuplicateGroup, keeperID: String) {
        Haptics.warning()
        for asset in group.all where asset.id != keeperID {
            trash.mark(asset, reason: vm.mode == .exact ? "Dubbel" : "Lijkend")
        }
        withAnimation { vm.remove(group) }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Picker("Soort", selection: $vm.mode) {
                    ForEach(DuplicatesViewModel.Mode.allCases) { mode in
                        Text(mode == .exact ? "Exacte dubbelen" : "Lijkende foto's").tag(mode)
                    }
                }
                .pickerStyle(.segmented)
                .padding()

                content
            }
            .navigationTitle("Dubbelen")
            .toolbar {
                if vm.isRefreshing {
                    ProgressView()
                } else {
                    Button {
                        Task { await vm.refresh(excluding: trash.trashedIDs) }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                    .accessibilityLabel("Opnieuw scannen")
                }
            }
        }
        .task {
            await vm.scan(excluding: trash.trashedIDs)
            if vm.mode == .similar { vm.ensureSimilarLoaded() }
        }
        .onChange(of: vm.mode) { _, newValue in
            visibleCount = Self.batchSize
            if newValue == .similar { vm.ensureSimilarLoaded() } else { vm.cancelSimilar() }
        }
        .onReceive(NotificationCenter.default.publisher(for: .photoLibraryDidChange)) { _ in
            Task { await vm.scan(excluding: trash.trashedIDs) }
        }
        .onDisappear { vm.cancelSimilar() }
        .fullScreenCover(item: $inspecting) { asset in
            PhotoZoomView(asset: asset, source: source)
        }
        .fullScreenCover(item: $playing) { asset in
            VideoPlayerScreen(asset: asset, source: source)
        }
    }

    @ViewBuilder
    private var content: some View {
        if vm.isLoading && (!vm.hasScanned || vm.isRefreshing) {
            loading(vm.isRefreshing ? "Opnieuw scannen…" : "Bibliotheek scannen…")
        } else if vm.mode == .similar && vm.isScanningSimilar {
            scanningSimilar
        } else if vm.groups.isEmpty {
            emptyState
        } else {
            list
        }
    }

    private func loading(_ text: String) -> some View {
        VStack(spacing: 12) {
            ProgressView()
            Text(text).foregroundStyle(.secondary)
            ElapsedTimeText()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var scanningSimilar: some View {
        VStack(spacing: 16) {
            ProgressView(value: vm.scanProgress) {
                Text("Lijkende foto's zoeken…")
            }
            .padding(.horizontal, 40)

            Text("\(Int(vm.scanProgress * 100))%")
                .font(.caption).foregroundStyle(.secondary)

            Button("Stoppen") { vm.cancelSimilar() }
                .buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var emptyState: some View {
        ContentUnavailableView(
            vm.mode == .exact ? "Geen dubbelen gevonden" : "Geen lijkende foto's",
            systemImage: "checkmark.seal",
            description: Text(vm.mode == .exact
                ? "Er zijn geen foto's met dezelfde metadata gevonden."
                : "Er zijn geen op elkaar lijkende foto's gevonden.")
        )
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var list: some View {
        List {
            if vm.mode == .similar {
                Text("Tik een foto aan om te vergroten. Kies met het vinkje welke je wilt behouden.")
                    .font(.caption).foregroundStyle(.secondary)
                    .listRowSeparator(.hidden)
            }
            ForEach(vm.groups.prefix(visibleCount)) { group in
                DuplicateGroupCell(
                    group: group,
                    source: source,
                    selectable: vm.mode == .similar,
                    onInspect: { inspect($0) },
                    onResolve: { keeperID in resolve(group, keeperID: keeperID) }
                )
            }

            if vm.groups.count > visibleCount {
                Button {
                    visibleCount += Self.batchSize
                } label: {
                    let remaining = vm.groups.count - visibleCount
                    Label("Toon meer (\(remaining) resterend)", systemImage: "chevron.down")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .listRowSeparator(.hidden)
            }
        }
    }
}

/// Eén duplicaat-groep: bekijk elk item (tik = vergroten/afspelen), kies welke je
/// behoudt, en gooi de rest weg.
private struct DuplicateGroupCell: View {
    let group: DuplicateGroup
    let source: PhotoSource
    /// Bij lijkende foto's kies je zelf de beste; bij exacte maakt het niet uit.
    let selectable: Bool
    var onInspect: (PhotoAsset) -> Void
    var onResolve: (_ keeperID: String) -> Void

    @State private var keeperID: String

    init(group: DuplicateGroup, source: PhotoSource, selectable: Bool,
         onInspect: @escaping (PhotoAsset) -> Void,
         onResolve: @escaping (String) -> Void) {
        self.group = group
        self.source = source
        self.selectable = selectable
        self.onInspect = onInspect
        self.onResolve = onResolve
        _keeperID = State(initialValue: group.keep.id)
    }

    private var reclaimable: Int64 {
        group.all.filter { $0.id != keeperID }.reduce(0) { $0 + $1.byteSize }
    }

    var body: some View {
        Section {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(group.all) { asset in
                        candidate(asset)
                    }
                }
                .padding(.vertical, 4)
            }

            Button(role: .destructive) {
                onResolve(keeperID)
            } label: {
                Label("Behoud gekozen, gooi \(group.count - 1) weg", systemImage: "trash")
            }
        } header: {
            Text("\(group.count) exemplaren · \(ByteFormatter.string(reclaimable)) te winnen")
        }
    }

    private func candidate(_ asset: PhotoAsset) -> some View {
        let isKeeper = asset.id == keeperID
        let thumb = PhotoThumbnail(asset: asset, source: source)
            .frame(width: 132, height: 132)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(alignment: .bottomTrailing) {
                if asset.isVideo {
                    Image(systemName: "play.circle.fill")
                        .foregroundStyle(.white, .black.opacity(0.4))
                        .padding(6)
                }
            }
            .overlay {
                if isKeeper {
                    RoundedRectangle(cornerRadius: 12).strokeBorder(.green, lineWidth: 3)
                }
            }
            .overlay(alignment: .topLeading) { selectionBadge(asset, isKeeper: isKeeper) }
            .contentShape(Rectangle())
            .onTapGesture { onInspect(asset) }

        return VStack(alignment: .leading, spacing: 3) {
            thumb
            if let folder = asset.folder {
                Label(folder, systemImage: "folder")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .truncationMode(.head)
                    .frame(width: 132, alignment: .leading)
            }
        }
    }

    @ViewBuilder
    private func selectionBadge(_ asset: PhotoAsset, isKeeper: Bool) -> some View {
        if selectable {
            Button { keeperID = asset.id } label: {
                Image(systemName: isKeeper ? "checkmark.circle.fill" : "circle")
                    .font(.title2)
                    .foregroundStyle(isKeeper ? .green : .white)
                    .padding(5)
                    .background(.black.opacity(0.3), in: Circle())
                    .padding(5)
            }
            .buttonStyle(.plain)
        } else if isKeeper {
            Text("Behouden")
                .font(.caption2).bold()
                .padding(4)
                .background(.green, in: Capsule())
                .foregroundStyle(.white)
                .padding(4)
        }
    }
}

/// Meelopende seconden-teller tijdens het scannen, zodat je ziet dat er iets
/// gebeurt (en hoe lang het duurt).
private struct ElapsedTimeText: View {
    @State private var start = Date()

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let seconds = max(0, Int(context.date.timeIntervalSince(start)))
            Text(seconds > 0 ? "\(seconds)s" : " ")
                .font(.caption).monospacedDigit()
                .foregroundStyle(.tertiary)
        }
    }
}

/// Compacte, leesbare weergave van bytes (bijv. "12,3 MB").
enum ByteFormatter {
    static func string(_ bytes: Int64) -> String {
        guard bytes > 0 else { return "0 MB" }
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }
}
