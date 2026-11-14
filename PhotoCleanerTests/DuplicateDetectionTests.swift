import CoreGraphics
import Foundation
@testable import PhotoCleaner
import Testing

/// First unit tests for the cleanup logic that decides what is offered for deletion:
/// metadata duplicates, perceptual (dHash) grouping and which photo of a group is kept.
private func asset(
    _ id: String,
    date: Date? = Date(timeIntervalSince1970: 1_700_000_000),
    width: Int = 4000,
    height: Int = 3000,
    bytes: Int64 = 1000
) -> PhotoAsset {
    PhotoAsset(
        id: id,
        creationDate: date,
        modificationDate: nil,
        pixelWidth: width,
        pixelHeight: height,
        byteSize: bytes,
        filename: nil
    )
}

struct DuplicateGroupTests {
    @Test func keepsTheHighestResolutionEvenIfTheFileIsSmaller() {
        let small = asset("small", width: 1000, height: 750, bytes: 9000)
        let large = asset("large", width: 4000, height: 3000, bytes: 2000)
        let group = DuplicateGroup.make(from: [small, large])
        #expect(group.keep.id == "large")
        #expect(group.duplicates.map(\.id) == ["small"])
    }

    @Test func breaksAResolutionTieOnFileSize() {
        let group = DuplicateGroup.make(from: [asset("a", bytes: 100), asset("b", bytes: 300)])
        #expect(group.keep.id == "b")
    }

    @Test func reclaimableBytesCountsOnlyTheDuplicates() {
        let group = DuplicateGroup.make(from: [
            asset("keep", bytes: 500), asset("d1", bytes: 200), asset("d2", bytes: 100),
        ])
        #expect(group.reclaimableBytes == 300)
        #expect(group.count == 3)
    }
}

struct DuplicateDetectorTests {
    @Test func groupsPhotosTakenInTheSameSecondWithTheSameSize() {
        let base = Date(timeIntervalSince1970: 1_700_000_000)
        let groups = DuplicateDetector.findDuplicates(in: [
            asset("a", date: base),
            asset("b", date: base.addingTimeInterval(0.4)),
            asset("other", date: base.addingTimeInterval(5)),
        ])
        #expect(groups.count == 1)
        #expect(Set(groups[0].all.map(\.id)) == ["a", "b"])
    }

    @Test func doesNotGroupDifferentDimensions() {
        let groups = DuplicateDetector.findDuplicates(in: [
            asset("a", width: 4000, height: 3000),
            asset("b", width: 3000, height: 4000),
        ])
        #expect(groups.isEmpty)
    }

    @Test func skipsPhotosWithoutACreationDate() {
        let groups = DuplicateDetector.findDuplicates(in: [asset("a", date: nil), asset("b", date: nil)])
        #expect(groups.isEmpty)
    }

    @Test func sortsGroupsByReclaimableSpace() {
        let t1 = Date(timeIntervalSince1970: 1_600_000_000)
        let t2 = Date(timeIntervalSince1970: 1_650_000_000)
        let groups = DuplicateDetector.findDuplicates(in: [
            asset("s1", date: t1, bytes: 10), asset("s2", date: t1, bytes: 10),
            asset("b1", date: t2, bytes: 900), asset("b2", date: t2, bytes: 800),
        ])
        #expect(groups.map(\.reclaimableBytes) == [800, 10])
    }
}

struct PerceptualDetectorTests {
    @Test func groupsHashesWithinTheDistanceTransitively() {
        // a–b and b–c are 2 bits apart, a–c is 4 bits apart: all three end up together.
        let items: [(asset: PhotoAsset, hash: UInt64)] = [
            (asset("a"), 0b0000),
            (asset("b"), 0b0011),
            (asset("c"), 0b1111),
            (asset("far"), UInt64.max),
        ]
        let groups = PerceptualDetector.group(items, maxDistance: 2)
        #expect(groups.count == 1)
        #expect(Set(groups[0].all.map(\.id)) == ["a", "b", "c"])
    }

    @Test func returnsNothingForASinglePhoto() {
        #expect(PerceptualDetector.group([(asset("a"), 0)], maxDistance: 10).isEmpty)
    }
}

struct ImageHashingTests {
    /// A 9×8 grey image whose columns get darker (or lighter) from left to right.
    private func gradient(darkening: Bool) -> CGImage {
        let width = 9, height = 8
        var pixels = [UInt8](repeating: 0, count: width * height)
        for row in 0 ..< height {
            for col in 0 ..< width {
                let level = 50 + col * 25
                pixels[row * width + col] = UInt8(darkening ? 300 - level : level)
            }
        }
        let provider = CGDataProvider(data: Data(pixels) as CFData)!
        return CGImage(
            width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 8,
            bytesPerRow: width, space: CGColorSpaceCreateDeviceGray(),
            bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.none.rawValue),
            provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent
        )!
    }

    @Test func setsEveryBitWhenEachPixelIsBrighterThanItsRightNeighbour() {
        #expect(ImageHashing.dHash(gradient(darkening: true)) == UInt64.max)
    }

    @Test func setsNoBitWhenEachPixelIsDarkerThanItsRightNeighbour() {
        #expect(ImageHashing.dHash(gradient(darkening: false)) == 0)
    }
}
