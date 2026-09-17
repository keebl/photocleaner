import SwiftUI

/// Laadt asynchroon een thumbnail uit de bron en toont een placeholder tijdens
/// het laden.
struct PhotoThumbnail: View {
    let asset: PhotoAsset
    let source: PhotoSource
    var targetSize: CGSize = CGSize(width: 400, height: 400)
    /// `.fill` (bijsnijden om te vullen) voor tegels; `.fit` (hele foto tonen)
    /// voor de grote weergave.
    var contentMode: ContentMode = .fill

    @State private var image: UIImage?
    @State private var isLoading: Bool

    init(asset: PhotoAsset, source: PhotoSource,
         targetSize: CGSize = CGSize(width: 400, height: 400),
         contentMode: ContentMode = .fill) {
        self.asset = asset
        self.source = source
        self.targetSize = targetSize
        self.contentMode = contentMode
        // Al gecached (voorgeladen)? Toon meteen, zonder placeholder-flits.
        let cached = source.cachedThumbnail(for: asset, targetSize: targetSize)
        _image = State(initialValue: cached)
        _isLoading = State(initialValue: cached == nil)
    }

    var body: some View {
        ZStack {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
            } else if asset.isVideo {
                // Meteen een film-icoon (geen spinner). Lukt het posterframe, dan
                // vervangt dat dit vanzelf; lukt het niet, dan blijft het icoon.
                Rectangle().fill(.quaternary)
                    .overlay {
                        Image(systemName: "film")
                            .font(.title2)
                            .foregroundStyle(.secondary)
                    }
            } else if isLoading {
                Rectangle().fill(.quaternary)
                    .overlay { ProgressView() }
            } else {
                // Foto geladen maar geen voorbeeld beschikbaar → rustig symbool.
                Rectangle().fill(.quaternary)
                    .overlay {
                        Image(systemName: "photo")
                            .font(.title2)
                            .foregroundStyle(.secondary)
                    }
            }
        }
        .clipped()
        .task(id: asset.id) {
            guard image == nil else { return }
            isLoading = true
            var result = await source.loadThumbnail(for: asset, targetSize: targetSize)
            // Eén nette retry bij een tijdelijke fout (bijv. iCloud/NAS-hapering).
            if result == nil, !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 400_000_000)
                if !Task.isCancelled {
                    result = await source.loadThumbnail(for: asset, targetSize: targetSize)
                }
            }
            image = result
            isLoading = false
        }
    }
}
