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

    init(asset: PhotoAsset, source: PhotoSource,
         targetSize: CGSize = CGSize(width: 400, height: 400),
         contentMode: ContentMode = .fill) {
        self.asset = asset
        self.source = source
        self.targetSize = targetSize
        self.contentMode = contentMode
        // Al gecached (voorgeladen)? Toon meteen, zonder placeholder-flits.
        _image = State(initialValue: source.cachedThumbnail(for: asset, targetSize: targetSize))
    }

    var body: some View {
        ZStack {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
            } else {
                Rectangle()
                    .fill(.quaternary)
                    .overlay { ProgressView() }
            }
        }
        .clipped()
        .task(id: asset.id) {
            if image == nil {
                image = await source.loadThumbnail(for: asset, targetSize: targetSize)
            }
        }
    }
}
