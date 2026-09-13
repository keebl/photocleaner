import Foundation

/// De gegevens om verbinding te maken met een NAS via SMB. Het **wachtwoord**
/// zit hier bewust niet in — dat bewaren we apart in de Keychain.
struct SMBCredentials: Codable, Equatable {
    /// Serveradres: IP of hostnaam, bijv. "192.168.1.10" of "synology.local".
    var host: String
    /// Naam van de gedeelde map (share), bijv. "photo" of "home".
    var share: String
    /// Gebruikersnaam. Leeg = gast-toegang.
    var username: String
    /// Optioneel Windows-domein (meestal leeg).
    var domain: String
    /// Optionele submap binnen de share (leeg = hele share).
    var folder: String

    init(host: String, share: String, username: String, domain: String = "", folder: String = "") {
        self.host = host
        self.share = share
        self.username = username
        self.domain = domain
        self.folder = folder
    }

    /// Genormaliseerd pad binnen de share waar we beginnen met zoeken.
    var normalizedFolder: String {
        let trimmed = folder.trimmingCharacters(in: CharacterSet(charactersIn: "/ "))
        return trimmed
    }

    /// Korte omschrijving voor in de UI, bijv. "photo op 192.168.1.10".
    var displayName: String {
        let base = share.isEmpty ? host : "\(share) op \(host)"
        return normalizedFolder.isEmpty ? base : "\(base)/\(normalizedFolder)"
    }

    /// `smb://host` — het serveradres voor AMSMB2.
    var serverURL: URL? {
        var comps = URLComponents()
        comps.scheme = "smb"
        comps.host = host
        return comps.url
    }

    // MARK: - Opslag (UserDefaults; wachtwoord apart in Keychain)

    private static let defaultsKey = "smbCredentials"
    static let keychainKey = "smbPassword"

    func save() {
        if let data = try? JSONEncoder().encode(self) {
            UserDefaults.standard.set(data, forKey: Self.defaultsKey)
        }
    }

    static func load() -> SMBCredentials? {
        guard let data = UserDefaults.standard.data(forKey: defaultsKey) else { return nil }
        return try? JSONDecoder().decode(SMBCredentials.self, from: data)
    }

    static func clear() {
        UserDefaults.standard.removeObject(forKey: defaultsKey)
        KeychainStore.remove(keychainKey)
    }
}
