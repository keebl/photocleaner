import SwiftUI

/// Instellingen: dagelijkse herinnering en (voorbereide) fotobronnen.
struct SettingsView: View {
    @EnvironmentObject private var notifications: NotificationManager
    @EnvironmentObject private var trash: TrashStore

    var body: some View {
        NavigationStack {
            Form {
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
                        HStack {
                            Image(systemName: kind.systemImage)
                                .frame(width: 28)
                                .foregroundStyle(kind.isAvailable ? Color.accentColor : .secondary)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(kind.displayName)
                                Text(kind.statusText)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            if kind.isAvailable {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(.green)
                            }
                        }
                        .opacity(kind.isAvailable ? 1 : 0.6)
                    }
                } header: {
                    Text("Fotobron")
                } footer: {
                    Text("Nu de iPhone-bibliotheek. Google Foto's en een NAS-map volgen via dezelfde opschoon-logica.")
                }

                Section("Over") {
                    LabeledContent("Prullenbak-retentie", value: "\(trash.retentionDays) dagen")
                    LabeledContent("Versie", value: appVersion)
                }
            }
            .navigationTitle("Instellingen")
        }
    }

    private var appVersion: String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "\(v) (\(b))"
    }
}
