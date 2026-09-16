import SwiftUI

/// Instellingen: dagelijkse herinnering en (voorbereide) fotobronnen.
struct SettingsView: View {
    @EnvironmentObject private var notifications: NotificationManager
    @EnvironmentObject private var trash: TrashStore
    @EnvironmentObject private var keep: KeepStore
    @EnvironmentObject private var theme: ThemeManager
    @EnvironmentObject private var sources: SourceManager

    @State private var showSMBConnect = false
    @State private var confirmResetKept = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Weergave") {
                    Picker("Thema", selection: $theme.theme) {
                        ForEach(AppTheme.allCases) { option in
                            Text(option.label).tag(option)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                Section {
                    Toggle("Dagelijkse herinnering", isOn: Binding(
                        get: { notifications.isEnabled },
                        set: { on in Task { await notifications.setEnabled(on) } }
                    ))

                    if notifications.isEnabled {
                        DatePicker(
                            "Tijdstip",
                            selection: Binding(
                                get: { notifications.time },
                                set: { t in Task { await notifications.setTime(t) } }
                            ),
                            displayedComponents: .hourAndMinute
                        )
                    }
                } header: {
                    Text("Op deze dag")
                } footer: {
                    Text("Krijg elke dag een seintje om je foto's van vandaag door de jaren heen te bekijken.")
                }

                Section {
                    ForEach(SourceKind.allCases) { kind in
                        Button {
                            selectSource(kind)
                        } label: {
                            HStack {
                                Image(systemName: kind.systemImage)
                                    .frame(width: 28)
                                    .foregroundStyle(sources.kind == kind ? Color.accentColor : .secondary)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(kind.displayName)
                                        .foregroundStyle(.primary)
                                    if kind == .nas {
                                        Text(sources.smbName ?? "Nog niet gekoppeld")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                Spacer()
                                if sources.kind == kind {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundStyle(.green)
                                }
                            }
                        }
                        .buttonStyle(.plain)
                    }

                    Button {
                        showSMBConnect = true
                    } label: {
                        Label(sources.hasSMB ? "NAS-koppeling wijzigen…" : "NAS koppelen…",
                              systemImage: sources.hasSMB ? "gearshape" : "externaldrive.badge.plus")
                    }

                    if sources.hasSMB {
                        Button {
                            Haptics.tap()
                            sources.smb.clearCaches()
                        } label: {
                            Label("NAS-cache legen", systemImage: "arrow.clockwise.circle")
                        }
                    }
                } header: {
                    Text("Fotobron")
                } footer: {
                    Text("Koppel je NAS rechtstreeks in de app via SMB: serveradres, share en inloggegevens. Je wachtwoord staat veilig in de Keychain. ‘NAS-cache legen’ gooit de bewaarde mappenlijst en previews weg voor een verse start (foto’s blijven ongemoeid).")
                }

                Section {
                    LabeledContent("Behouden foto's", value: "\(keep.ids.count)")
                    Button(role: .destructive) {
                        confirmResetKept = true
                    } label: {
                        Label("Behoud-keuzes wissen", systemImage: "arrow.counterclockwise")
                    }
                    .disabled(keep.ids.isEmpty)
                } header: {
                    Text("Opschonen")
                } footer: {
                    Text("Foto's die je hebt behouden komen niet meer terug in de opschoon-weergaven. Wissen laat ze weer verschijnen.")
                }

                Section("Over") {
                    LabeledContent("Prullenbak-retentie", value: "\(trash.retentionDays) dagen")
                    LabeledContent("Versie", value: appVersion)
                }
            }
            .navigationTitle("Instellingen")
            .sheet(isPresented: $showSMBConnect) {
                SMBConnectView(source: sources.smb) { sources.activateSMB() }
            }
            .confirmationDialog(
                "Alle \(keep.ids.count) behoud-keuzes wissen?",
                isPresented: $confirmResetKept,
                titleVisibility: .visible
            ) {
                Button("Wissen", role: .destructive) {
                    Haptics.warning()
                    keep.reset()
                }
                Button("Annuleren", role: .cancel) {}
            } message: {
                Text("De foto's zelf blijven ongemoeid; ze verschijnen weer in de opschoon-weergaven.")
            }
        }
    }

    private func selectSource(_ kind: SourceKind) {
        if kind == .nas && !sources.hasSMB {
            showSMBConnect = true
        } else {
            sources.select(kind)
        }
    }

    private var appVersion: String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "\(v) (\(b))"
    }
}
