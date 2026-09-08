import Foundation

/// Eén foto die de gebruiker heeft weggetikt en die in de retentieperiode zit.
struct TrashItem: Codable, Identifiable {
    let id: String          // PhotoAsset.id
    let markedDate: Date
    var reason: String      // bijv. "Dubbel" of "Op deze dag"
}

/// Eigen prullenbak met retentie: foto's blijven eerst in de bibliotheek maar
/// verdwijnen uit de opschoon-weergaven. Pas na de retentieperiode (of handmatig
/// "legen") worden ze echt uit de bron verwijderd.
///
/// De lijst wordt bewaard in Documents/trash.json en overleeft dus herstarts.
@MainActor
final class TrashStore: ObservableObject {
    @Published private(set) var items: [TrashItem] = []

    let retentionDays = 30

    private let fileURL: URL
    private let calendar = Calendar.current

    init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        fileURL = docs.appendingPathComponent("trash.json")
        load()
    }

    // MARK: - Query

    func contains(_ id: String) -> Bool {
        items.contains { $0.id == id }
    }

    /// Alle ids in de prullenbak — handig om ze uit andere weergaven te filteren.
    var trashedIDs: Set<String> {
        Set(items.map(\.id))
    }

    func expiryDate(for item: TrashItem) -> Date {
        calendar.date(byAdding: .day, value: retentionDays, to: item.markedDate) ?? item.markedDate
    }

    /// Hele dagen tot definitieve verwijdering (0 = vandaag verlopen).
    func daysLeft(for item: TrashItem) -> Int {
        let days = calendar.dateComponents([.day], from: Date(), to: expiryDate(for: item)).day ?? 0
        return max(0, days)
    }

    var expiredItems: [TrashItem] {
        items.filter { expiryDate(for: $0) <= Date() }
    }

    // MARK: - Muteren

    func mark(_ asset: PhotoAsset, reason: String) {
        guard !contains(asset.id) else { return }
        items.append(TrashItem(id: asset.id, markedDate: Date(), reason: reason))
        save()
    }

    func restore(_ id: String) {
        items.removeAll { $0.id == id }
        save()
    }

    /// Verwijdert deze ids uit de prullenbak-administratie (aan te roepen nadat ze
    /// echt uit de bron zijn verwijderd).
    func forget(_ ids: [String]) {
        let set = Set(ids)
        items.removeAll { set.contains($0.id) }
        save()
    }

    // MARK: - Persistentie

    private func load() {
        guard let data = try? Data(contentsOf: fileURL) else { return }
        items = (try? JSONDecoder().decode([TrashItem].self, from: data)) ?? []
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(items) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }
}
