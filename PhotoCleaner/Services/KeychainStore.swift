import Foundation
import Security

/// Kleine wrapper om een geheim (zoals een NAS-wachtwoord) veilig in de iOS
/// Keychain te bewaren. Wachtwoorden komen zo nooit in UserDefaults of in een
/// gewoon bestand terecht.
enum KeychainStore {
    /// Bewaart (of overschrijft) een geheim onder een sleutel.
    @discardableResult
    static func set(_ value: String, for key: String) -> Bool {
        let data = Data(value.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key
        ]
        SecItemDelete(query as CFDictionary)

        var attributes = query
        attributes[kSecValueData as String] = data
        // Bewuste afweging (Security Hotspot – beoordeeld als veilig):
        // Dit item vereist géén losse authenticatie (Face ID/Touch ID) bij het lezen.
        // Dat is hier nodig omdat het NAS-wachtwoord automatisch gelezen moet worden
        // om te (her)verbinden en om meerdere SMB-verbindingen (previews/scan) te
        // openen; een biometrie-prompt per lezing zou die achtergrond/parallelle
        // toegang breken en de gebruikerservaring verslechteren.
        // Wél restrictief beschermd: `ThisDeviceOnly` (nooit iCloud-sync of back-up
        // naar een ander toestel) en `AfterFirstUnlock` (alleen leesbaar nadat het
        // toestel sinds de start één keer is ontgrendeld).
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        return SecItemAdd(attributes as CFDictionary, nil) == errSecSuccess
    }

    /// Leest een geheim, of `nil` als het er niet is.
    static func get(_ key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    /// Verwijdert een geheim.
    static func remove(_ key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key
        ]
        SecItemDelete(query as CFDictionary)
    }
}
