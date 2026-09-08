import Foundation

/// Vindt foto's die op elkaar *lijken* (niet per se byte-identiek): bewerkte,
/// gecomprimeerde of opnieuw geëxporteerde versies. Werkt op perceptual hashes
/// (dHash) en groepeert foto's waarvan de hashes dicht bij elkaar liggen
/// (Hamming-afstand ≤ `maxDistance`).
enum PerceptualDetector {
    static func group(
        _ items: [(asset: PhotoAsset, hash: UInt64)],
        maxDistance: Int
    ) -> [DuplicateGroup] {
        let n = items.count
        guard n > 1 else { return [] }

        // Union-find om lijkende foto's in dezelfde groep te clusteren.
        var parent = Array(0..<n)
        func find(_ x: Int) -> Int {
            var root = x
            while parent[root] != root { parent[root] = parent[parent[root]]; root = parent[root] }
            return root
        }
        func union(_ a: Int, _ b: Int) {
            let ra = find(a), rb = find(b)
            if ra != rb { parent[ra] = rb }
        }

        for i in 0..<n {
            for j in (i + 1)..<n where hamming(items[i].hash, items[j].hash) <= maxDistance {
                union(i, j)
            }
        }

        var buckets: [Int: [PhotoAsset]] = [:]
        for i in 0..<n { buckets[find(i), default: []].append(items[i].asset) }

        return buckets.values
            .filter { $0.count > 1 }
            .map { DuplicateGroup.make(from: $0) }
            .sorted { $0.reclaimableBytes > $1.reclaimableBytes }
    }

    private static func hamming(_ a: UInt64, _ b: UInt64) -> Int {
        (a ^ b).nonzeroBitCount
    }
}
