import SwiftUI

/// Formulier om binnen de app verbinding te maken met een NAS via SMB.
/// Serveradres, share, gebruiker en wachtwoord vul je hier in; bij succes wordt de
/// NAS de actieve bron. Het wachtwoord gaat naar de Keychain.
struct SMBConnectView: View {
    let source: SMBSource
    var onConnected: () -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var host = ""
    @State private var share = ""
    @State private var username = ""
    @State private var password = ""
    @State private var folder = ""
    @State private var connecting = false
    @State private var errorMessage: String?

    private var canConnect: Bool {
        !host.trimmingCharacters(in: .whitespaces).isEmpty &&
        !share.trimmingCharacters(in: .whitespaces).isEmpty &&
        !connecting
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    LabeledField(title: "Serveradres", text: $host,
                                 placeholder: "192.168.1.10 of nas.local",
                                 keyboard: .URL)
                    LabeledField(title: "Share", text: $share, placeholder: "bijv. photo")
                    LabeledField(title: "Submap (optioneel)", text: $folder,
                                 placeholder: "bijv. Vakanties/2024")
                } header: {
                    Text("Server")
                } footer: {
                    Text("Het adres en de share van je NAS. De submap is optioneel — leeg = de hele share.")
                }

                Section {
                    LabeledField(title: "Gebruikersnaam", text: $username,
                                 placeholder: "leeg = gast")
                    HStack {
                        Text("Wachtwoord").frame(width: 130, alignment: .leading)
                            .foregroundStyle(.secondary)
                        SecureField("Wachtwoord", text: $password)
                    }
                } header: {
                    Text("Inloggen")
                } footer: {
                    Text("Je wachtwoord wordt veilig in de iOS-sleutelhanger (Keychain) bewaard, niet in de app zelf.")
                }

                if let errorMessage {
                    Section {
                        Label(errorMessage, systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(.red)
                            .font(.callout)
                    }
                }

                Section {
                    Button {
                        Task { await connect() }
                    } label: {
                        HStack {
                            Spacer()
                            if connecting {
                                ProgressView().padding(.trailing, 6)
                                Text("Verbinden…")
                            } else {
                                Text("Verbinden")
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
                            Label("NAS-koppeling verwijderen", systemImage: "trash")
                        }
                    }
                }
            }
            .navigationTitle("NAS koppelen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Annuleren") { dismiss() }
                }
            }
            .onAppear(perform: prefill)
            .interactiveDismissDisabled(connecting)
        }
    }

    private func prefill() {
        guard let creds = source.credentials else { return }
        host = creds.host
        share = creds.share
        username = creds.username
        folder = creds.folder
    }

    private func connect() async {
        errorMessage = nil
        connecting = true
        defer { connecting = false }

        let creds = SMBCredentials(
            host: host.trimmingCharacters(in: .whitespaces),
            share: share.trimmingCharacters(in: .whitespaces),
            username: username.trimmingCharacters(in: .whitespaces),
            domain: "",
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
