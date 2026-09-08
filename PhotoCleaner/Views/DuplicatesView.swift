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
    @Published private(set) var isLoading = false

    private let source: PhotoSource

    init(source: PhotoSource) {
        self.source = source
    }

    var groups: [DuplicateGroup] {
        mode == .exact ? exactGroups : similarGroups
    }

    func scan(excluding hidden: Set<String>) async {
        isLoading = true
        defer { isLoading = false }

        let all = await source.fetchAllPhotos()
        let visible = all.filter { !hidden.contains($0.id) }

        // Exacte dubbelen op metadata.
        exactGroups = DuplicateDetector.findDuplicates(in: visible)

        // Lijkende foto's op perceptual hash.
        var hashed: [(asset: PhotoAsset, hash: UInt64)] = []
        for asset in visible {
            if let hash = await source.perceptualHash(for: asset) {
                hashed.append((asset, hash))
            }
        }
        similarGroups = PerceptualDetector.group(hashed, maxDistance: 8)
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

                Group {
                    if vm.isLoading {
                        ProgressView("Bibliotheek scannen…")
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if vm.groups.isEmpty {
                        emptyState
                    } else {
                        list
                    }
                }
            }
            .navigationTitle("Dubbelen")
            .toolbar {
                Button {
                    Task { await vm.scan(excluding: trash.trashedIDs) }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
            }
        }
        .task { await vm.scan(excluding: trash.trashedIDs) }
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
