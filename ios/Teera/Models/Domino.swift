import Foundation

/// A single bone. Stored canonically with low <= high so that 6|3 and 3|6 are the same tile.
public struct Domino: Codable, Hashable, Comparable, Identifiable {
    public let low: Int
    public let high: Int

    public var id: String { "\(low)|\(high)" }

    public init(low: Int, high: Int) {
        if low <= high {
            self.low = low
            self.high = high
        } else {
            self.low = high
            self.high = low
        }
    }

    public var isDouble: Bool { low == high }

    /// Spots on the tile.
    public var pips: Int { low + high }

    public func matches(_ face: Int) -> Bool {
        face == low || face == high
    }

    public func otherFace(_ face: Int) -> Int? {
        if face == low { return high }
        if face == high { return low }
        return nil
    }

    public static func < (lhs: Domino, rhs: Domino) -> Bool {
        if lhs.high != rhs.high {
            return lhs.high < rhs.high
        }
        return lhs.low < rhs.low
    }

    public var description: String { "\(low)|\(high)" }

    public static func of(_ a: Int, _ b: Int) -> Domino {
        Domino(low: min(a, b), high: max(a, b))
    }

    public static func parse(_ text: String) -> Domino? {
        let parts = text.components(separatedBy: CharacterSet(charactersIn: "|- :"))
            .filter { !$0.isEmpty }
        guard parts.count == 2,
              let a = Int(parts[0].trimmingCharacters(in: .whitespaces)),
              let b = Int(parts[1].trimmingCharacters(in: .whitespaces)) else {
            return nil
        }
        return of(a, b)
    }
}

public enum Pack {
    public static let maxFace = 6
    public static let size = 28
    public static let handSize = 7
    public static let players = 4

    public static let openingDouble = Domino(low: 6, high: 6)
    public static let teeraTiles = [Domino(low: 0, high: 0), Domino(low: 0, high: 1)]

    public static let allTiles: [Domino] = {
        var tiles: [Domino] = []
        for high in 0...maxFace {
            for low in 0...high {
                tiles.append(Domino(low: low, high: high))
            }
        }
        return tiles
    }()

    public static let doubles: [Domino] = allTiles.filter { $0.isDouble }
}

public extension Sequence where Element == Domino {
    func pipTotal() -> Int {
        reduce(0) { $0 + $1.pips }
    }
}

public func teamOf(_ seat: Int) -> Int {
    seat % 2
}

public func seatsOfTeam(_ team: Int) -> [Int] {
    (0..<Pack.players).filter { teamOf($0) == team }
}

public func otherTeam(_ team: Int) -> Int {
    1 - team
}
