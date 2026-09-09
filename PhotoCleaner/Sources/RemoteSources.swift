import UIKit

/// Fout wanneer een bron nog niet is geconfigureerd/ingelogd.
enum SourceError: LocalizedError {
    case notConfigured(String)
    var errorDescription: String? {
        switch self {
        case .notConfigured(let name): return "\(name) is nog niet geconfigureerd."
        }
    }
}

// MARK: - Google Foto's (voorbereid)

/// Skeleton voor een Google Foto's-bron. De architectuur is klaar; de echte
/// koppeling vereist externe stappen die buiten de app vallen:
///
/// 1. Google Cloud-project + OAuth-client (iOS) aanmaken en een consent-scherm
///    instellen; scope `photoslibrary.readonly` (+ verwijderen kan Google's API
///    NIET — Google Foto's staat verwijderen via de API niet toe; daar wordt het
///    "weggooien" dus markeren/exporteren i.p.v. echt wissen).
/// 2. OAuth-inlog in de app (bijv. AppAuth / GoogleSignIn), tokens veilig bewaren
///    in de Keychain.
/// 3. `mediaItems.search` met een datumfilter voor "op deze dag"; `mediaItems.list`
///    voor de volledige scan; thumbnails via de `baseUrl` (=wxhxc-varianten).
final class GooglePhotosSource: PhotoSource {
    let displayName = "Google Foto's"

    func fetchAllPhotos() async -> [PhotoAsset] { [] }
    func fetchPhotos(onMonth month: Int, day: Int) async -> [PhotoAsset] { [] }
    func loadThumbnail(for asset: PhotoAsset, targetSize: CGSize) async -> UIImage? { nil }
    func perceptualHash(for asset: PhotoAsset) async -> UInt64? { nil }
    func byteSizes(for assets: [PhotoAsset]) async -> [String: Int64] { [:] }
    func delete(_ assets: [PhotoAsset]) async throws { throw SourceError.notConfigured(displayName) }
}

// MARK: - NAS-map (voorbereid)

/// Skeleton voor een NAS-bron (een gedeelde map op het netwerk). De architectuur
/// is klaar; de echte koppeling vereist:
///
/// 1. Verbinden met de share (SMB/WebDAV). iOS heeft geen ingebouwde SMB-API;
///    opties: WebDAV via URLSession, of de gebruiker de map laten kiezen met
///    `UIDocumentPicker` (security-scoped bookmarks) als de NAS via de
///    Bestanden-app bereikbaar is.
/// 2. Bestanden inlezen, EXIF-datum lezen voor "op deze dag" en duplicaten.
/// 3. Verwijderen = bestand echt van de share halen (met dezelfde 30-dagen
///    prullenbak-logica ervoor).
final class NASFolderSource: PhotoSource {
    let displayName = "NAS-map"

    func fetchAllPhotos() async -> [PhotoAsset] { [] }
    func fetchPhotos(onMonth month: Int, day: Int) async -> [PhotoAsset] { [] }
    func loadThumbnail(for asset: PhotoAsset, targetSize: CGSize) async -> UIImage? { nil }
    func perceptualHash(for asset: PhotoAsset) async -> UInt64? { nil }
    func byteSizes(for assets: [PhotoAsset]) async -> [String: Int64] { [:] }
    func delete(_ assets: [PhotoAsset]) async throws { throw SourceError.notConfigured(displayName) }
}
