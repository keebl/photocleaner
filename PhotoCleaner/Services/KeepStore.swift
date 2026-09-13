import Foundation

/// Onthoudt welke foto's/filmpjes de gebruiker bewust heeft **behouden**, zodat
/// ze niet opnieuw langskomen — ook niet als je van dag/maand/jaar of bron wisselt.
///
/// Dit is de tegenhanger van de prullenbak: weggegooide items zitten in
/// [[TrashStore]], behouden items hier. De lijst staat in Documents/kept.json en
/// overleeft herstarts.
@MainActor
final class KeepStore: ObservableObject {
    @Published private(set) var ids: Set<String> = []

    private let fileURL: URL

    init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        fileURL = docs.appendingPathComponent("kept.json")
        load()
    }

    func contains(_ id: String) -> Bool { ids.contains(id) }

    func keep(_ id: String) {
        guard !ids.contains(id) else { return }
        ids.insert(id)
        save()
    }

    func unkeep(_ id: String) {
        guard ids.contains(id) else { return }
        ids.remove(id)
        save()
    }

    /// Wist alle behoud-keuzes; alle eerder behouden foto's komen weer in de
    /// opschoon-weergaven.
    func reset() {
        guard !ids.isEmpty else { return }
        ids.removeAll()
        save()
    }

    // MARK: - Persistentie

    private func load() {
        guard let data = try? Data(contentsOf: fileURL),
              let list = try? JSONDecoder().decode([String].self, from: data) else { return }
        ids = Set(list)
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(Array(ids)) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }
}
