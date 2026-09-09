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
            image = await source.loadThumbnail(for: asset, targetSize: targetSize)
        }
    }
}
