import SwiftUI

/// Instellingen: dagelijkse herinnering en (voorbereide) fotobronnen.
struct SettingsView: View {
    @EnvironmentObject private var notifications: NotificationManager
    @EnvironmentObject private var trash: TrashStore
    @EnvironmentObject private var theme: ThemeManager
    @EnvironmentObject private var sources: SourceManager

    @State private var showFolderPicker = false

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
                                        Text(sources.nasFolderName ?? "Geen map gekozen")
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
                        showFolderPicker = true
                    } label: {
                        Label(sources.nasFolderName == nil ? "NAS-map kiezen…" : "Andere map kiezen…",
                              systemImage: "folder.badge.plus")
                    }
                } header: {
                    Text("Fotobron")
                } footer: {
                    Text("Koppel je NAS eenmalig in de iOS Bestanden-app (SMB) en kies hier die map. Alles blijft lokaal binnen de app.")
                }

                Section("Over") {
                    LabeledContent("Prullenbak-retentie", value: "\(trash.retentionDays) dagen")
                    LabeledContent("Versie", value: appVersion)
                }
            }
            .navigationTitle("Instellingen")
            .sheet(isPresented: $showFolderPicker) {
                FolderPicker { url in
                    sources.setNASFolder(url)
                }
                .ignoresSafeArea()
            }
        }
    }

    private func selectSource(_ kind: SourceKind) {
        if kind == .nas && sources.nasFolderName == nil {
            showFolderPicker = true
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
