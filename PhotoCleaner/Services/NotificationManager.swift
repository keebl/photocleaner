import UserNotifications
import SwiftUI

/// Regelt de dagelijkse lokale "Op deze dag"-herinnering.
///
/// Gebruikt een herhalende `UNCalendarNotificationTrigger` op een vast tijdstip.
/// De voorkeuren (aan/uit + tijd) worden in UserDefaults bewaard.
@MainActor
final class NotificationManager: ObservableObject {
    @Published private(set) var isEnabled: Bool
    @Published private(set) var time: Date

    private let reminderID = "onthisday.daily"
    private let defaults = UserDefaults.standard

    init() {
        isEnabled = defaults.bool(forKey: "reminderEnabled")
        let hour = defaults.object(forKey: "reminderHour") as? Int ?? 9
        let minute = defaults.object(forKey: "reminderMinute") as? Int ?? 0
        time = Calendar.current.date(from: DateComponents(hour: hour, minute: minute)) ?? Date()
    }

    func setEnabled(_ on: Bool) async {
        isEnabled = on
        defaults.set(on, forKey: "reminderEnabled")
        await sync()
    }

    func setTime(_ newTime: Date) async {
        time = newTime
        let comps = Calendar.current.dateComponents([.hour, .minute], from: newTime)
        defaults.set(comps.hour ?? 9, forKey: "reminderHour")
        defaults.set(comps.minute ?? 0, forKey: "reminderMinute")
        await sync()
    }

    /// (Her)plant of verwijdert de herinnering op basis van de huidige voorkeuren.
    /// Veilig om bij elke app-start aan te roepen.
    func sync() async {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [reminderID])

        guard isEnabled else { return }
        guard await requestAuthorizationIfNeeded() else {
            // Toestemming geweigerd: zet de schakelaar terug uit.
            isEnabled = false
            defaults.set(false, forKey: "reminderEnabled")
            return
        }

        let content = UNMutableNotificationContent()
        content.title = "Op deze dag"
        content.body = "Bekijk je foto's van vandaag door de jaren heen."
        content.sound = .default

        let comps = Calendar.current.dateComponents([.hour, .minute], from: time)
        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: true)
        let request = UNNotificationRequest(identifier: reminderID, content: content, trigger: trigger)
        try? await center.add(request)
    }

    private func requestAuthorizationIfNeeded() async -> Bool {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral:
            return true
        case .notDetermined:
            return (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        default:
            return false
        }
    }
}
