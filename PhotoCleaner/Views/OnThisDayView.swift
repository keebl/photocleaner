import SwiftUI

@MainActor
final class OnThisDayViewModel: ObservableObject {
    @Published private(set) var assets: [PhotoAsset] = []
    @Published private(set) var isLoading = false

    private let source: PhotoSource

    init(source: PhotoSource) {
        self.source = source
    }

    func load() async {
        isLoading = true
        let now = Date()
        let comps = Calendar.current.dateComponents([.month, .day], from: now)
        assets = await source.fetchPhotos(onMonth: comps.month ?? 1, day: comps.day ?? 1)
        isLoading = false
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

/// "Op deze dag": foto's van vandaag in eerdere jaren, met per foto de keuze
/// behouden of weggooien.
struct OnThisDayView: View {
    let source: PhotoSource

    @EnvironmentObject private var trash: TrashStore
    @StateObject private var vm: OnThisDayViewModel

    /// Foto's die de gebruiker deze sessie bewust heeft behouden.
    @State private var kept: Set<String> = []

    init(source: PhotoSource) {
        self.source = source
        _vm = StateObject(wrappedValue: OnThisDayViewModel(source: source))
    }

    private var hidden: Set<String> {
        trash.trashedIDs.union(kept)
    }

    var body: some View {
        NavigationStack {
            Group {
                if vm.isLoading {
                    ProgressView("Foto's laden…")
                } else {
                    content
                }
            }
            .navigationTitle("Op deze dag")
        }
        .task { await vm.load() }
    }

    @ViewBuilder
    private var content: some View {
        let groups = vm.groupedByYear(excluding: hidden)
        if groups.isEmpty {
            ContentUnavailableView(
                "Niets voor vandaag",
                systemImage: "calendar.badge.checkmark",
                description: Text("Geen (resterende) foto's die op deze dag in eerdere jaren zijn gemaakt.")
            )
        } else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 24) {
                    ForEach(groups, id: \.year) { group in
                        Section {
                            ForEach(group.assets) { asset in
                                photoCard(asset)
                            }
                        } header: {
                            Text(yearHeader(group.year))
                                .font(.title3).bold()
                                .padding(.horizontal)
                        }
                    }
                }
                .padding(.vertical)
            }
        }
    }

    private func photoCard(_ asset: PhotoAsset) -> some View {
        VStack(spacing: 0) {
            PhotoThumbnail(asset: asset, source: source, targetSize: CGSize(width: 1000, height: 1000))
                .aspectRatio(4.0 / 3.0, contentMode: .fit)
                .frame(maxWidth: .infinity)
                .background(.quaternary)

            HStack {
                Button {
                    withAnimation { _ = kept.insert(asset.id) }
                } label: {
                    Label("Behouden", systemImage: "checkmark")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)

                Button(role: .destructive) {
                    withAnimation { trash.mark(asset, reason: "Op deze dag") }
                } label: {
                    Label("Weggooien", systemImage: "trash")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
            }
            .padding(12)
        }
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(.separator))
        .padding(.horizontal)
    }

    private func yearHeader(_ year: Int) -> String {
        let ago = Calendar.current.component(.year, from: Date()) - year
        switch ago {
        case 0: return "Dit jaar"
        case 1: return "1 jaar geleden · \(year)"
        default: return "\(ago) jaar geleden · \(year)"
        }
    }
}
