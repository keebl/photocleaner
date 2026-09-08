import SwiftUI

@MainActor
final class DuplicatesViewModel: ObservableObject {
    @Published private(set) var groups: [DuplicateGroup] = []
    @Published private(set) var isLoading = false

    private let source: PhotoSource

    init(source: PhotoSource) {
        self.source = source
    }

    func scan(excluding hidden: Set<String>) async {
        isLoading = true
        let all = await source.fetchAllPhotos()
        let visible = all.filter { !hidden.contains($0.id) }
        groups = DuplicateDetector.findDuplicates(in: visible)
        isLoading = false
    }
}

/// "Dubbelen": groepen van foto's die volgens de metadata gelijk zijn. Per groep
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
            Group {
                if vm.isLoading {
                    ProgressView("Bibliotheek scannen…")
                } else if vm.groups.isEmpty {
                    ContentUnavailableView(
                        "Geen dubbelen gevonden",
                        systemImage: "checkmark.seal",
                        description: Text("Er zijn geen foto's met dezelfde metadata gevonden.")
                    )
                } else {
                    list
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
                                trash.mark(dup, reason: "Dubbel")
                            }
                        }
                    } label: {
                        Label("Behoud beste, gooi \(group.duplicates.count) dubbele weg", systemImage: "trash")
                    }
                } header: {
                    Text("\(group.count) exemplaren · \(ByteFormatter.string(group.reclaimableBytes)) te winnen")
                }
            }
        }
    }

    private func thumb(_ asset: PhotoAsset, isKeeper: Bool) -> some View {
        PhotoThumbnail(asset: asset, source: source)
            .frame(width: 100, height: 100)
            .clipShape(RoundedRectangle(cornerRadius: 10))
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
