import Foundation

public struct Seat: Codable, Identifiable, Hashable {
    public var id: Int { seat }
    public let seat: Int
    public let playerId: String
    public let name: String
    public var isBot: Bool
    public var connected: Bool
    public var avatar: String
    public var standIn: String?
    public var micOn: Bool

    public init(
        seat: Int,
        playerId: String,
        name: String,
        isBot: Bool = false,
        connected: Bool = true,
        avatar: String = "",
        standIn: String? = nil,
        micOn: Bool = false
    ) {
        self.seat = seat
        self.playerId = playerId
        self.name = name
        self.isBot = isBot
        self.connected = connected
        self.avatar = avatar
        self.standIn = standIn
        self.micOn = micOn
    }
}

public enum Phase: String, Codable, Hashable {
    case REDEAL_OFFER
    case IN_PLAY
    case RESCUE
    case TEERA_OFFER
    case GAME_OVER
    case MATCH_OVER
}

public enum GameEnd: String, Codable, Hashable {
    case X
    case TEERA
    case BLOCKED
}

public struct GameResult: Codable, Hashable {
    public let gameNumber: Int
    public let end: GameEnd
    public let winnerSeat: Int?
    public let handPips: [Int]
    public let scored: [Int]
    public let nextOpener: Int

    public init(
        gameNumber: Int,
        end: GameEnd,
        winnerSeat: Int?,
        handPips: [Int],
        scored: [Int],
        nextOpener: Int
    ) {
        self.gameNumber = gameNumber
        self.end = end
        self.winnerSeat = winnerSeat
        self.handPips = handPips
        self.scored = scored
        self.nextOpener = nextOpener
    }
}

public enum RedealReason: String, Codable, Hashable {
    case FIVE_DOUBLES
    case SIX_OF_A_NUMBER
    case OPPONENTS_CANNOT_ANSWER
    case FOUR_DOUBLES_BY_CHOICE
}

public enum Rejection: String, Codable, Hashable {
    case NOT_YOUR_TURN
    case WRONG_PHASE
    case TILE_NOT_IN_HAND
    case MUST_OPEN_WITH_A_DOUBLE
    case MUST_OPEN_WITH_THE_SIX
    case MUST_OPEN_A_FRESH_SIDE
    case TILE_DOES_NOT_MATCH
    case NO_ARM_GIVEN
    case MUST_PLAY_IF_ABLE
    case CANNOT_PLAY_FOR_THEM
    case TOO_HEAVY_FOR_A_TEERA
}

public enum MoveType: String, Codable {
    case play
    case give
    case playFor
    case teera
    case carryOn
    case redeal
    case keepHand
}

public struct Move: Codable, Hashable {
    public let type: MoveType
    public let domino: Domino?
    public let arm: Arm?

    enum CodingKeys: String, CodingKey {
        case type = "t"
        case domino
        case arm
    }

    public init(type: MoveType, domino: Domino? = nil, arm: Arm? = nil) {
        self.type = type
        self.domino = domino
        self.arm = arm
    }

    public static func play(_ domino: Domino, arm: Arm? = nil) -> Move {
        Move(type: .play, domino: domino, arm: arm)
    }

    public static func give(_ domino: Domino) -> Move {
        Move(type: .give, domino: domino)
    }

    public static func playFor(_ domino: Domino, arm: Arm) -> Move {
        Move(type: .playFor, domino: domino, arm: arm)
    }

    public static let teera = Move(type: .teera)
    public static let carryOn = Move(type: .carryOn)
    public static let redeal = Move(type: .redeal)
    public static let keepHand = Move(type: .keepHand)
}

public struct GameEvent: Codable, Hashable {
    public let type: String
    public let seat: Int?
    public let domino: Domino?
    public let arm: Arm?
    public let from: Int?
    public let to: Int?
    public let rescuer: Int?
    public let stuck: Int?
    public let gameNumber: Int?
    public let opener: Int?

    enum CodingKeys: String, CodingKey {
        case type = "t"
        case seat
        case domino
        case arm
        case from
        case to
        case rescuer
        case stuck
        case gameNumber
        case opener
    }
}
