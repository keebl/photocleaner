import SwiftUI

@MainActor
final class TrashViewModel: ObservableObject {
    /// Opzoektabel id → PhotoAsset, voor thumbnails en verwijderen.
    @Published private(set) var assetsByID: [String: PhotoAsset] = [:]
    @Published var errorMessage: String?

    private let source: PhotoSource

    init(source: PhotoSource) {
        self.source = source
    }

    func load(ids: [String]) async {
        let assets = await source.assets(withIDs: ids)
        assetsByID = Dictionary(assets.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }

    /// Verwijdert de opgegeven items definitief uit de bron en daarna uit de
    /// prullenbak-administratie. iOS toont hierbij een systeembevestiging.
    func permanentlyDelete(_ items: [TrashItem], from trash: TrashStore) async {
        let assets = items.compactMap { assetsByID[$0.id] }
        do {
            if !assets.isEmpty {
                try await source.delete(assets)
            }
            // Ook ids zonder bijbehorende asset (al elders verwijderd) opruimen.
            trash.forget(items.map(\.id))
        } catch {
            errorMessage = "Verwijderen geannuleerd of mislukt."
        }
    }
}

/// De eigen prullenbak met 30-dagen retentie: terugzetten of definitief legen.
struct TrashView: View {
    let source: PhotoSource

    @EnvironmentObject private var trash: TrashStore
    @StateObject private var vm: TrashViewModel

    init(source: PhotoSource) {
        self.source = source
        _vm = StateObject(wrappedValue: TrashViewModel(source: source))
    }

    var body: some View {
        NavigationStack {
            Group {
                if trash.items.isEmpty {
                    ContentUnavailableView(
                        "Prullenbak is leeg",
                        systemImage: "trash",
                        description: Text("Foto's die je weggooit verschijnen hier en blijven \(trash.retentionDays) dagen herstelbaar.")
                    )
                } else {
                    list
                }
            }
            .navigationTitle("Prullenbak")
            .toolbar { toolbar }
            .task(id: trash.items.map(\.id).joined()) {
                await vm.load(ids: trash.items.map(\.id))
            }
            .alert("Er ging iets mis", isPresented: Binding(
                get: { vm.errorMessage != nil },
                set: { if !$0 { vm.errorMessage = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(vm.errorMessage ?? "")
            }
        }
    }

    private var list: some View {
        List {
            Section {
                ForEach(trash.items) { item in
                    row(item)
                }
            } footer: {
                Text("Na \(trash.retentionDays) dagen worden foto's definitief verwijderd. Definitief verwijderen zet ze nog in Apple's ‘Recent verwijderd’.")
            }
        }
    }

    private func row(_ item: TrashItem) -> some View {
        HStack(spacing: 12) {
            if let asset = vm.assetsByID[item.id] {
                PhotoThumbnail(asset: asset, source: source)
                    .frame(width: 56, height: 56)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            } else {
                RoundedRectangle(cornerRadius: 8).fill(.quaternary).frame(width: 56, height: 56)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(item.reason).font(.subheadline).bold()
                Text(daysLeftText(item)).font(.caption).foregroundStyle(.secondary)
            }

            Spacer()

            Button("Terugzetten") { trash.restore(item.id) }
                .buttonStyle(.bordered)
                .controlSize(.small)
        }
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .primaryAction) {
            Menu {
                Button(role: .destructive) {
                    Task { await vm.permanentlyDelete(trash.expiredItems, from: trash) }
                } label: {
                    Label("Verlopen legen (\(trash.expiredItems.count))", systemImage: "trash")
                }
                .disabled(trash.expiredItems.isEmpty)

                Button(role: .destructive) {
                    Task { await vm.permanentlyDelete(trash.items, from: trash) }
                } label: {
                    Label("Alles nu definitief verwijderen", systemImage: "trash.fill")
                }
            } label: {
                Image(systemName: "ellipsis.circle")
            }
        }
    }

    private func daysLeftText(_ item: TrashItem) -> String {
        let days = trash.daysLeft(for: item)
        return days == 0 ? "Verloopt vandaag" : "Nog \(days) dagen herstelbaar"
    }
}
