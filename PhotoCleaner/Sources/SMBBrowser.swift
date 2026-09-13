import Foundation
import AMSMB2

/// Bladert door een NAS om te tónen welke shares en mappen er zijn, zodat de
/// gebruiker er één kan kiezen. Los van [[SMBSource]] (de actieve bron): deze
/// wordt alleen tijdens het koppelen gebruikt en houdt eigen, tijdelijke
/// verbindingen aan.
actor SMBBrowser {
    private let host: String
    private let username: String
    private let password: String
    private let domain: String

    private var serverClient: SMB2Manager?
    private var shareClients: [String: SMB2Manager] = [:]

    init(host: String, username: String, password: String, domain: String = "") {
        self.host = host
        self.username = username
        self.password = password
        self.domain = domain
    }

    /// De shares (gedeelde mappen) op de server. Verborgen/beheer-shares (eindigen
    /// op `$`) worden weggelaten.
    func shares() async throws -> [String] {
        let client = try serverManager()
        let list = try await client.listShares()
        return list
            .map(\.name)
            .filter { !$0.hasSuffix("$") && $0.uppercased() != "IPC$" }
            .sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
    }

    /// De submappen binnen een share op het opgegeven pad (leeg = de wortel van de
    /// share). Bestanden en verborgen mappen worden weggelaten.
    func folders(share: String, path: String) async throws -> [String] {
        let client = try await shareManager(share)
        let entries = try await client.contentsOfDirectory(atPath: path, recursive: false)
        return entries
            .filter { $0.isDirectory }
            .compactMap { $0.name }
            .filter { !$0.hasPrefix(".") }
            .sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
    }

    // MARK: - Verbindingen

    private func serverManager() throws -> SMB2Manager {
        if let serverClient { return serverClient }
        let m = try makeManager()
        serverClient = m
        return m
    }

    private func shareManager(_ share: String) async throws -> SMB2Manager {
        if let existing = shareClients[share] { return existing }
        let m = try makeManager()
        try await m.connectShare(name: share)
        shareClients[share] = m
        return m
    }

    private func makeManager() throws -> SMB2Manager {
        var comps = URLComponents()
        comps.scheme = "smb"
        comps.host = host
        guard let url = comps.url else { throw SMBError.badServer }
        let credential = username.isEmpty ? nil
            : URLCredential(user: username, password: password, persistence: .forSession)
        guard let m = SMB2Manager(url: url, domain: domain, credential: credential) else {
            throw SMBError.badServer
        }
        return m
    }
}
