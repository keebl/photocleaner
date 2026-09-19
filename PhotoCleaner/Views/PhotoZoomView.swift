import SwiftUI

/// Volledig scherm om een foto van dichtbij te bekijken: knijp om te zoomen,
/// sleep om te verschuiven, dubbeltik om te wisselen. Als `onKeep`/`onDiscard`
/// zijn meegegeven, kun je op normale grootte horizontaal vegen om te behouden
/// (rechts) of weg te gooien (links) — net als in de swipe-stapel.
struct PhotoZoomView: View {
    let asset: PhotoAsset
    let source: PhotoSource
    var onKeep: (() -> Void)? = nil
    var onDiscard: (() -> Void)? = nil

    @Environment(\.dismiss) private var dismiss

    @State private var image: UIImage?
    @State private var scale: CGFloat = 1
    @GestureState private var pinch: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero
    @State private var swipe: CGSize = .zero
    @State private var committing = false

    private let maxScale: CGFloat = 5
    private let swipeThreshold: CGFloat = 90

    /// Vegen om te beslissen kan alleen op normale grootte en als er acties zijn.
    private var canDecide: Bool { (onKeep != nil || onDiscard != nil) && scale == 1 && !committing }

    private var imageOffset: CGSize {
        scale > 1 ? offset : CGSize(width: swipe.width, height: 0)
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .scaleEffect(scale * pinch)
                    .offset(imageOffset)
                    .rotationEffect(.degrees(scale == 1 ? Double(swipe.width / 24) : 0))
                    .gesture(magnification)
                    .simultaneousGesture(drag)
                    .onTapGesture(count: 2) { toggleZoom() }
            } else {
                ProgressView().tint(.white)
            }

            if scale == 1 { feedback }

            VStack {
                topBar
                Spacer()
                if canDecide { legend }
            }
        }
        .task {
            image = await source.loadThumbnail(
                for: asset,
                targetSize: CGSize(width: 2400, height: 2400)
            )
        }
    }

    private var topBar: some View {
        HStack(spacing: 18) {
            if let dateText = asset.dateText {
                Text(dateText)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12).padding(.vertical, 6)
                    .background(.black.opacity(0.4), in: Capsule())
            }
            Spacer()
            ShareButton(asset: asset, source: source)
                .font(.title2)
                .foregroundStyle(.white)
            Button { dismiss() } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.title)
                    .foregroundStyle(.white, .black.opacity(0.4))
            }
        }
        .padding()
    }

    private var legend: some View {
        HStack {
            HStack(spacing: 4) { Image(systemName: "arrow.left"); Text("Weggooien") }
                .foregroundStyle(.red)
            Spacer()
            HStack(spacing: 4) { Text("Behouden"); Image(systemName: "arrow.right") }
                .foregroundStyle(.green)
        }
        .font(.caption.weight(.medium))
        .padding(.horizontal, 24)
        .padding(.bottom, 24)
    }

    @ViewBuilder
    private var feedback: some View {
        if swipe.width > 0 {
            stamp(system: "checkmark.circle.fill", color: .green,
                  opacity: Double(min(swipe.width / swipeThreshold, 1)))
        } else if swipe.width < 0 {
            stamp(system: "trash.circle.fill", color: .red,
                  opacity: Double(min(-swipe.width / swipeThreshold, 1)))
        }
    }

    private func stamp(system: String, color: Color, opacity: Double) -> some View {
        Image(systemName: system)
            .font(.system(size: 96))
            .foregroundStyle(.white, color)
            .opacity(opacity)
    }

    private var magnification: some Gesture {
        MagnificationGesture()
            .updating($pinch) { value, state, _ in state = value }
            .onEnded { value in
                scale = min(max(scale * value, 1), maxScale)
                if scale == 1 { withAnimation(.spring) { offset = .zero; lastOffset = .zero } }
            }
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 10)
            .onChanged { value in
                if scale > 1 {
                    offset = CGSize(width: lastOffset.width + value.translation.width,
                                    height: lastOffset.height + value.translation.height)
                } else if canDecide {
                    swipe = value.translation
                }
            }
            .onEnded { value in
                if scale > 1 {
                    lastOffset = offset
                } else if canDecide {
                    if value.translation.width > swipeThreshold {
                        commit(keep: true)
                    } else if value.translation.width < -swipeThreshold {
                        commit(keep: false)
                    } else {
                        withAnimation(.spring) { swipe = .zero }
                    }
                }
            }
    }

    private func commit(keep: Bool) {
        committing = true
        if keep { Haptics.tap() } else { Haptics.warning() }
        withAnimation(.easeOut(duration: 0.25)) { swipe.width = keep ? 700 : -700 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.22) {
            if keep { onKeep?() } else { onDiscard?() }
            dismiss()
        }
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
