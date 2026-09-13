import SwiftUI

/// Twee stappen om een NAS te koppelen:
/// 1. Server + inloggegevens invullen en verbinden.
/// 2. Door de beschikbare shares en mappen bladeren en er één kiezen.
/// De gekozen map wordt de bron; de app doorzoekt daaronder ook alle submappen.
struct SMBConnectView: View {
    let source: SMBSource
    var onConnected: () -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var host = ""
    @State private var username = ""
    @State private var password = ""
    @State private var domain = ""

    @State private var browser: SMBBrowser?
    @State private var path: [BrowseNode] = []

    @State private var connecting = false     // stap 1: inloggen
    @State private var finalizing = false     // stap 2: map bevestigen
    @State private var errorMessage: String?

    /// Waar we naartoe navigeren in de bladeraar.
    enum BrowseNode: Hashable {
        case shares
        case folder(share: String, path: String, title: String)
    }

    private var canConnect: Bool {
        !host.trimmingCharacters(in: .whitespaces).isEmpty && !connecting
    }

    var body: some View {
        NavigationStack(path: $path) {
            loginForm
                .navigationTitle("NAS koppelen")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Annuleren") { dismiss() }
                    }
                }
                .navigationDestination(for: BrowseNode.self) { node in
                    destination(node)
                }
        }
        .overlay { if finalizing { finalizingOverlay } }
        .alert("Er ging iets mis", isPresented: Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "")
        }
        .interactiveDismissDisabled(connecting || finalizing)
        .onAppear(perform: prefill)
    }

    // MARK: - Stap 1: inloggen

    private var loginForm: some View {
        Form {
            Section {
                LabeledField(title: "Serveradres", text: $host,
                             placeholder: "192.168.1.10 of nas.local", keyboard: .URL)
                LabeledField(title: "Gebruikersnaam", text: $username, placeholder: "leeg = gast")
                HStack {
                    Text("Wachtwoord").frame(width: 130, alignment: .leading)
                        .foregroundStyle(.secondary)
                    SecureField("Wachtwoord", text: $password)
                }
            } header: {
                Text("Server")
            } footer: {
                Text("Het adres van je NAS en je inloggegevens. Je wachtwoord wordt veilig in de iOS-sleutelhanger (Keychain) bewaard.")
            }

            Section {
                Button {
                    Task { await login() }
                } label: {
                    HStack {
                        Spacer()
                        if connecting {
                            ProgressView().padding(.trailing, 6)
                            Text("Verbinden…")
                        } else {
                            Text("Verbinden en mappen tonen")
                        }
                        Spacer()
                    }
                }
                .disabled(!canConnect)
            }

            if source.isConfigured {
                Section {
                    Button(role: .destructive) {
                        source.disconnect()
                        dismiss()
                    } label: {
                        Label("Huidige NAS-koppeling verwijderen", systemImage: "trash")
                    }
                }
            }
        }
    }

    private func login() async {
        errorMessage = nil
        connecting = true
        defer { connecting = false }

        let b = SMBBrowser(
            host: host.trimmingCharacters(in: .whitespaces),
            username: username.trimmingCharacters(in: .whitespaces),
            password: password,
            domain: domain
        )
        do {
            // Meteen de shares ophalen: dat valideert de login én vult stap 2.
            _ = try await b.shares()
            browser = b
            Haptics.tap()
            path = [.shares]
        } catch {
            Haptics.warning()
            errorMessage = Self.friendlyMessage(error)
        }
    }

    // MARK: - Stap 2: bladeren

    @ViewBuilder
    private func destination(_ node: BrowseNode) -> some View {
        if let browser {
            switch node {
            case .shares:
                SMBShareList(browser: browser) { share in
                    path.append(.folder(share: share, path: "", title: share))
                }
            case let .folder(share, folderPath, title):
                SMBFolderList(
                    browser: browser, share: share, path: folderPath, title: title,
                    onOpen: { name in
                        let next = folderPath.isEmpty ? name : "\(folderPath)/\(name)"
                        path.append(.folder(share: share, path: next, title: name))
                    },
                    onChoose: { Task { await finalize(share: share, folder: folderPath) } }
                )
            }
        }
    }

    private func finalize(share: String, folder: String) async {
        errorMessage = nil
        finalizing = true
        defer { finalizing = false }

        let creds = SMBCredentials(
            host: host.trimmingCharacters(in: .whitespaces),
            share: share,
            username: username.trimmingCharacters(in: .whitespaces),
            domain: domain,
            folder: folder
        )
        do {
            try await source.connect(creds, password: password)
            Haptics.success()
            onConnected()
            dismiss()
        } catch {
            Haptics.warning()
            errorMessage = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        }
    }

    // MARK: - Hulp

    private var finalizingOverlay: some View {
        ZStack {
            Color.black.opacity(0.35).ignoresSafeArea()
            VStack(spacing: 12) {
                ProgressView()
                Text("Koppelen…").font(.callout)
            }
            .padding(24)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
        }
    }

    private func prefill() {
        guard let creds = source.credentials else { return }
        host = creds.host
        username = creds.username
        domain = creds.domain
    }

    static func friendlyMessage(_ error: Error) -> String {
        let ns = error as NSError
        let text = ns.localizedDescription.lowercased()
        if text.contains("auth") || text.contains("password") || text.contains("logon")
            || text.contains("access") || text.contains("credential") || ns.code == 13 {
            return "Inloggen mislukt — controleer je gebruikersnaam en wachtwoord."
        }
        if text.contains("connection refused") || text.contains("timed out") || text.contains("no route")
            || text.contains("unreach") || ns.code == 61 || ns.code == 60 || ns.code == 65 {
            return "Kan de server niet bereiken. Controleer het serveradres en of je op hetzelfde netwerk zit als de NAS (wifi/VPN)."
        }
        // libsmb2-fouten hebben vaak een cryptische of lege omschrijving; geef dan
        // een bruikbare hint die de meest voorkomende oorzaken dekt.
        return "Verbinden mislukt. Controleer het serveradres, je inloggegevens, en of je op hetzelfde netwerk zit als de NAS."
    }
}

/// Lijst met shares op de server.
private struct SMBShareList: View {
    let browser: SMBBrowser
    var onSelect: (String) -> Void

    @State private var shares: [String] = []
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        List {
            if loading {
                loadingRow
            } else if let error {
                Text(error).foregroundStyle(.red).font(.callout)
            } else if shares.isEmpty {
                Text("Geen shares gevonden.").foregroundStyle(.secondary)
            } else {
                Section("Kies een share") {
                    ForEach(shares, id: \.self) { share in
                        Button { onSelect(share) } label: {
                            Label(share, systemImage: "externaldrive.connected.to.line.below")
                        }
                        .foregroundStyle(.primary)
                    }
                }
            }
        }
        .navigationTitle("Shares")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    private var loadingRow: some View {
        HStack { ProgressView(); Text("Laden…").foregroundStyle(.secondary) }
    }

    private func load() async {
        loading = true
        defer { loading = false }
        do { shares = try await browser.shares() }
        catch { self.error = SMBConnectView.friendlyMessage(error) }
    }
}

/// Lijst met submappen binnen een share, met de mogelijkheid deze map te kiezen.
private struct SMBFolderList: View {
    let browser: SMBBrowser
    let share: String
    let path: String
    let title: String
    var onOpen: (String) -> Void
    var onChoose: () -> Void

    @State private var folders: [String] = []
    @State private var loading = true
    @State private var error: String?

    private var isShareRoot: Bool { path.isEmpty }

    var body: some View {
        List {
            Section {
                Button(action: onChoose) {
                    Label(isShareRoot ? "Hele share gebruiken" : "Deze map gebruiken",
                          systemImage: "checkmark.circle.fill")
                }
                .foregroundStyle(.green)
            } footer: {
                Text("De app toont alle foto's en filmpjes hierin, inclusief submappen.")
            }

            Section("Submappen") {
                if loading {
                    HStack { ProgressView(); Text("Laden…").foregroundStyle(.secondary) }
                } else if let error {
                    Text(error).foregroundStyle(.red).font(.callout)
                } else if folders.isEmpty {
                    Text("Geen submappen.").foregroundStyle(.secondary)
                } else {
                    ForEach(folders, id: \.self) { folder in
                        Button { onOpen(folder) } label: {
                            HStack {
                                Label(folder, systemImage: "folder")
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.caption).foregroundStyle(.tertiary)
                            }
                        }
                        .foregroundStyle(.primary)
                    }
                }
            }
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .task(id: path) { await load() }
    }

    private func load() async {
        loading = true
        defer { loading = false }
        do { folders = try await browser.folders(share: share, path: path) }
        catch { self.error = SMBConnectView.friendlyMessage(error) }
    }
}

/// Een label links, tekstveld rechts — houdt het formulier rustig en uitgelijnd.
private struct LabeledField: View {
    let title: String
    @Binding var text: String
    var placeholder = ""
    var keyboard: UIKeyboardType = .default

    var body: some View {
        HStack {
            Text(title).frame(width: 130, alignment: .leading)
                .foregroundStyle(.secondary)
            TextField(placeholder, text: $text)
                .keyboardType(keyboard)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
        }
    }
}
