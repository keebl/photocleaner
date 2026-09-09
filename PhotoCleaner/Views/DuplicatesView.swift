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

    private func computeSimilar() async {
        isScanningSimilar = true
        scanProgress = 0
        defer { isScanningSimilar = false }

        let toScan = visible
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

    init(source: PhotoSource) {
        self.source = source
        _vm = StateObject(wrappedValue: DuplicatesViewModel(source: source))
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
                Button {
                    Task { await vm.refresh(excluding: trash.trashedIDs) }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .accessibilityLabel("Opnieuw scannen")
            }
        }
        .task {
            await vm.scan(excluding: trash.trashedIDs)
            if vm.mode == .similar { vm.ensureSimilarLoaded() }
        }
        .onChange(of: vm.mode) { _, newValue in
            if newValue == .similar { vm.ensureSimilarLoaded() } else { vm.cancelSimilar() }
        }
        .onReceive(NotificationCenter.default.publisher(for: .photoLibraryDidChange)) { _ in
            Task { await vm.scan(excluding: trash.trashedIDs) }
        }
        .onDisappear { vm.cancelSimilar() }
    }

    @ViewBuilder
    private var content: some View {
        if vm.isLoading && !vm.hasScanned {
            loading("Bibliotheek scannen…")
        } else if vm.mode == .similar && vm.isScanningSimilar {
            scanningSimilar
        } else if vm.groups.isEmpty {
            emptyState
        } else {
            list
        }
    }

    private func loading(_ text: String) -> some View {
        ProgressView(text).frame(maxWidth: .infinity, maxHeight: .infinity)
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
            ForEach(vm.groups) { group in
                Section {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(group.all) { asset in
                                thumb(asset, isKeeper: asset.id == group.keep.id)
                            }
                        }
                        .padding(.vertical, 4)
                    }

                    Button(role: .destructive) {
                        Haptics.warning()
                        withAnimation {
                            for dup in group.duplicates {
                                trash.mark(dup, reason: vm.mode == .exact ? "Dubbel" : "Lijkend")
                            }
                        }
                    } label: {
                        Label("Behoud beste, gooi \(group.duplicates.count) weg", systemImage: "trash")
                    }
                } header: {
                    Text("\(group.count) exemplaren · \(ByteFormatter.string(group.reclaimableBytes)) te winnen")
                }
            }
        }
    }

    private func thumb(_ asset: PhotoAsset, isKeeper: Bool) -> some View {
        PhotoThumbnail(asset: asset, source: source)
            .frame(width: 132, height: 132)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(alignment: .topLeading) {
                if isKeeper {
                    Text("Behouden")
                        .font(.caption2).bold()
                        .padding(4)
                        .background(.green, in: Capsule())
                        .foregroundStyle(.white)
                        .padding(4)
                }
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
