import SwiftUI

@MainActor
final class OnThisDayViewModel: ObservableObject {
    @Published private(set) var assets: [PhotoAsset] = []
    @Published private(set) var isLoading = false

    private let source: PhotoSource

    init(source: PhotoSource) {
        self.source = source
    }

    func load(for date: Date) async {
        isLoading = true
        let comps = Calendar.current.dateComponents([.month, .day], from: date)
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

/// "Op deze dag": foto's van een gekozen datum in eerdere jaren, met per foto de
/// keuze behouden of weggooien. Blader met de dag terug/vooruit of kies een datum.
struct OnThisDayView: View {
    let source: PhotoSource

    @EnvironmentObject private var trash: TrashStore
    @StateObject private var vm: OnThisDayViewModel

    @State private var selectedDate = Date()
    @State private var showDatePicker = false
    /// Foto's die de gebruiker deze sessie bewust heeft behouden.
    @State private var kept: Set<String> = []

    init(source: PhotoSource) {
        self.source = source
        _vm = StateObject(wrappedValue: OnThisDayViewModel(source: source))
    }

    private var hidden: Set<String> {
        trash.trashedIDs.union(kept)
    }

    private var isToday: Bool {
        Calendar.current.isDate(selectedDate, inSameDayAs: Date())
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                dateBar
                Divider()
                Group {
                    if vm.isLoading {
                        ProgressView("Foto's laden…")
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else {
                        content
                    }
                }
            }
            .navigationTitle("Op deze dag")
            .toolbar {
                if !isToday {
                    Button("Vandaag") { changeDate(to: Date()) }
                }
            }
            .sheet(isPresented: $showDatePicker) {
                datePickerSheet
            }
        }
        .task(id: dateKey) { await vm.load(for: selectedDate) }
    }

    // MARK: - Datumbalk

    private var dateBar: some View {
        HStack {
            Button { shiftDay(-1) } label: {
                Image(systemName: "chevron.left").font(.title3.weight(.semibold))
                    .frame(width: 44, height: 44)
            }

            Spacer()

            Button { showDatePicker = true } label: {
                VStack(spacing: 2) {
                    Text(dayTitle).font(.headline)
                    Text(isToday ? "Vandaag" : "Kies datum")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .buttonStyle(.plain)

            Spacer()

            Button { shiftDay(1) } label: {
                Image(systemName: "chevron.right").font(.title3.weight(.semibold))
                    .frame(width: 44, height: 44)
            }
        }
        .padding(.horizontal)
        .padding(.vertical, 6)
    }

    private var datePickerSheet: some View {
        NavigationStack {
            DatePicker(
                "Datum",
                selection: $selectedDate,
                displayedComponents: .date
            )
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

    // MARK: - Inhoud

    @ViewBuilder
    private var content: some View {
        let groups = vm.groupedByYear(excluding: hidden)
        if groups.isEmpty {
            ContentUnavailableView(
                "Niets op deze dag",
                systemImage: "calendar.badge.checkmark",
                description: Text("Geen (resterende) foto's die op \(dayTitle) in eerdere jaren zijn gemaakt.")
            )
        } else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 28) {
                    ForEach(groups, id: \.year) { group in
                        VStack(alignment: .leading, spacing: 12) {
                            Text(yearHeader(group.year))
                                .font(.title3).bold()
                                .padding(.horizontal)
                            ForEach(group.assets) { asset in
                                photoCard(asset)
                            }
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

            HStack(spacing: 10) {
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
        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(.separator.opacity(0.5)))
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .shadow(color: .black.opacity(0.12), radius: 8, y: 3)
        .padding(.horizontal)
    }

    // MARK: - Datumlogica

    /// Sleutel die de `.task` opnieuw laat lopen als de dag verandert.
    private var dateKey: String {
        let c = Calendar.current.dateComponents([.month, .day], from: selectedDate)
        return "\(c.month ?? 0)-\(c.day ?? 0)"
    }

    private func shiftDay(_ days: Int) {
        if let d = Calendar.current.date(byAdding: .day, value: days, to: selectedDate) {
            changeDate(to: d)
        }
    }

    private func changeDate(to date: Date) {
        selectedDate = date
        kept.removeAll()
    }

    private var dayTitle: String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "nl_NL")
        f.dateFormat = "d MMMM"
        return f.string(from: selectedDate)
    }

    private func yearHeader(_ year: Int) -> String {
        let reference = Calendar.current.component(.year, from: selectedDate)
        let ago = reference - year
        switch ago {
        case ..<0:  return "\(year)"
        case 0:     return "Dit jaar"
        case 1:     return "1 jaar geleden · \(year)"
        default:    return "\(ago) jaar geleden · \(year)"
        }
    }
}
