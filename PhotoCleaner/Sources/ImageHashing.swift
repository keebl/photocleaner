import CoreGraphics

/// Gedeelde perceptual-hash (dHash) berekening op een CGImage.
enum ImageHashing {
    /// dHash: teken op 9×8 grijswaarden en vergelijk elke pixel met z'n
    /// rechterbuur → 64 bits.
    static func dHash(_ cgImage: CGImage) -> UInt64? {
        let width = 9, height = 8
        var pixels = [UInt8](repeating: 0, count: width * height)
        guard let context = CGContext(
            data: &pixels,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width,
            space: CGColorSpaceCreateDeviceGray(),
            bitmapInfo: CGImageAlphaInfo.none.rawValue
        ) else { return nil }

        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))

        var hash: UInt64 = 0
        var bit: UInt64 = 0
        for row in 0..<height {
            for col in 0..<(width - 1) {
                if pixels[row * width + col] > pixels[row * width + col + 1] {
                    hash |= (1 << bit)
                }
                bit += 1
            }
        }
        return hash
    }
}
