import SwiftUI

/// Laadt asynchroon een thumbnail uit de bron en toont een placeholder tijdens
/// het laden.
struct PhotoThumbnail: View {
    let asset: PhotoAsset
    let source: PhotoSource
    var targetSize: CGSize = CGSize(width: 400, height: 400)

    @State private var image: UIImage?

    var body: some View {
        ZStack {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
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
