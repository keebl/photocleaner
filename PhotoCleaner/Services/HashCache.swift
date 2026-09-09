import Foundation

/// Bewaart berekende perceptual hashes op schijf, zodat een tweede scan vrijwel
/// meteen klaar is. Gevalideerd op de wijzigingsdatum van de foto: is een foto
/// bewerkt, dan wordt de oude hash genegeerd en opnieuw berekend.
///
/// Thread-veilig (de scan draait buiten de main-thread) via een lock.
final class HashCache {
    private struct Entry: Codable {
        let hash: UInt64
        let mod: Double   // modificationDate als timestamp (0 = onbekend)
    }

    private let url: URL
    private let lock = NSLock()
    private var entries: [String: Entry]
    private var dirty = false

    init(filename: String = "perceptualHashes.json") {
        let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        url = dir.appendingPathComponent(filename)
        if let data = try? Data(contentsOf: url),
           let decoded = try? JSONDecoder().decode([String: Entry].self, from: data) {
            entries = decoded
        } else {
            entries = [:]
        }
    }

    func hash(for id: String, modifiedAt: Date?) -> UInt64? {
        lock.lock(); defer { lock.unlock() }
        guard let entry = entries[id] else { return nil }
        // Foto bewerkt sinds we hashten? Dan opnieuw berekenen.
        if let mod = modifiedAt, abs(mod.timeIntervalSince1970 - entry.mod) > 1 { return nil }
        return entry.hash
    }

    func set(_ hash: UInt64, for id: String, modifiedAt: Date?) {
        lock.lock(); defer { lock.unlock() }
        entries[id] = Entry(hash: hash, mod: modifiedAt?.timeIntervalSince1970 ?? 0)
        dirty = true
    }

    /// Schrijft wijzigingen weg (alleen als er iets veranderd is).
    func flush() {
        lock.lock()
        guard dirty else { lock.unlock(); return }
        let snapshot = entries
        dirty = false
        lock.unlock()

        if let data = try? JSONEncoder().encode(snapshot) {
            try? data.write(to: url, options: .atomic)
        }
    }
}
