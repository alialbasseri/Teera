import Foundation

public enum Arm: String, Codable, CaseIterable, Hashable {
    case NORTH
    case EAST
    case SOUTH
    case WEST

    public var ordinal: Int {
        switch self {
        case .NORTH: return 0
        case .EAST: return 1
        case .SOUTH: return 2
        case .WEST: return 3
        }
    }

    public static func from(ordinal: Int) -> Arm? {
        guard ordinal >= 0 && ordinal < allCases.count else { return nil }
        return allCases[ordinal]
    }
}

public struct LaidTile: Codable, Equatable, Hashable {
    public let domino: Domino
    public let inner: Int
    public let outer: Int

    public var isDouble: Bool { domino.isDouble }

    public init(domino: Domino, inner: Int, outer: Int) {
        self.domino = domino
        self.inner = inner
        self.outer = outer
    }
}

public struct Board: Codable, Equatable, Hashable {
    public var centre: Domino?
    public var arms: [[LaidTile]]

    public var isEmpty: Bool { centre == nil }

    public var centreFace: Int? { centre?.high }

    public init(centre: Domino? = nil, arms: [[LaidTile]] = Array(repeating: [], count: 4)) {
        self.centre = centre
        self.arms = arms.count == 4 ? arms : Array(repeating: [], count: 4)
    }

    public func tilesOn(_ arm: Arm) -> [LaidTile] {
        guard arm.ordinal < arms.count else { return [] }
        return arms[arm.ordinal]
    }

    public func endOf(_ arm: Arm) -> Int? {
        let tiles = tilesOn(arm)
        return tiles.last?.outer ?? centreFace
    }

    public func isOpened(_ arm: Arm) -> Bool {
        !tilesOn(arm).isEmpty
    }

    public var openedArms: Int {
        arms.filter { !$0.isEmpty }.count
    }

    public var inOpeningPhase: Bool {
        centre != nil && openedArms < Arm.allCases.count
    }

    public func tiles() -> [Domino] {
        var result: [Domino] = []
        if let centre = centre {
            result.append(centre)
        }
        for arm in arms {
            result.append(contentsOf: arm.map { $0.domino })
        }
        return result
    }

    public func armsAccepting(_ tile: Domino) -> [Arm] {
        guard let _ = centre else { return [] }
        return Arm.allCases.filter { arm in
            guard let end = endOf(arm) else { return false }
            if inOpeningPhase {
                // Must open fresh sides first (rule 12)
                return !isOpened(arm) && tile.matches(end)
            } else {
                return tile.matches(end)
            }
        }
    }

    public func acceptsAnywhere(_ tile: Domino) -> Bool {
        !armsAccepting(tile).isEmpty
    }
}
