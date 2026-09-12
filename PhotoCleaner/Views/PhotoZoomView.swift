import SwiftUI

/// Volledig scherm om een foto van dichtbij te bekijken: knijp om te zoomen,
/// sleep om te verschuiven, dubbeltik om te wisselen.
struct PhotoZoomView: View {
    let asset: PhotoAsset
    let source: PhotoSource

    @Environment(\.dismiss) private var dismiss

    @State private var image: UIImage?
    @State private var scale: CGFloat = 1
    @GestureState private var pinch: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero

    private let maxScale: CGFloat = 5

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .scaleEffect(scale * pinch)
                    .offset(offset)
                    .gesture(magnification)
                    .simultaneousGesture(dragWhenZoomed)
                    .onTapGesture(count: 2) { toggleZoom() }
            } else {
                ProgressView().tint(.white)
            }

            VStack {
                HStack {
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title)
                            .foregroundStyle(.white, .black.opacity(0.4))
                    }
                    .padding()
                }
                Spacer()
            }
        }
        .task {
            image = await source.loadThumbnail(
                for: asset,
                targetSize: CGSize(width: 2400, height: 2400)
            )
        }
    }

    private var magnification: some Gesture {
        MagnificationGesture()
            .updating($pinch) { value, state, _ in state = value }
            .onEnded { value in
                scale = min(max(scale * value, 1), maxScale)
                if scale == 1 { withAnimation(.spring) { offset = .zero; lastOffset = .zero } }
            }
    }

    private var dragWhenZoomed: some Gesture {
        DragGesture()
            .onChanged { value in
                guard scale > 1 else { return }
                offset = CGSize(width: lastOffset.width + value.translation.width,
                                height: lastOffset.height + value.translation.height)
            }
            .onEnded { _ in lastOffset = offset }
    }

    private func toggleZoom() {
        withAnimation(.spring) {
            if scale > 1 {
                scale = 1; offset = .zero; lastOffset = .zero
            } else {
                scale = 2.5
            }
        }
    }
}
