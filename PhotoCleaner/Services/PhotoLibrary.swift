import Photos
import SwiftUI

/// Vereenvoudigde weergave van de toegangsstatus tot de fotobibliotheek.
enum LibraryAccess {
    case notDetermined
    case authorized   // volledige toegang
    case limited      // gebruiker koos een selectie foto's
    case denied

    var canReadPhotos: Bool { self == .authorized || self == .limited }
}

/// Beheert de toestemming voor de fotobibliotheek.
@MainActor
final class PhotoLibrary: ObservableObject {
    @Published private(set) var access: LibraryAccess

    init() {
        access = Self.map(PHPhotoLibrary.authorizationStatus(for: .readWrite))
    }

    /// Vraagt (indien nodig) toegang en werkt `access` bij.
    func requestAccess() async {
        let status = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        access = Self.map(status)
    }

    /// Opent de Instellingen-app op de pagina van deze app (voor als toegang is geweigerd).
    func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    private static func map(_ status: PHAuthorizationStatus) -> LibraryAccess {
        switch status {
        case .authorized: return .authorized
        case .limited: return .limited
        case .denied, .restricted: return .denied
        case .notDetermined: return .notDetermined
        @unknown default: return .denied
        }
    }
}
