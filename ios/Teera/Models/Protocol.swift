import Foundation

public enum Emote: String, Codable, CaseIterable, Hashable {
    case GULP, DRUMROLL, SAD, SLAP, NO_WAY, CHAT, SHEEL, SPLOOSH, WALLAH, GHASHANA, KHATLA
    case FACE_JOY, FACE_ROFL, FACE_SMIRK, FACE_COOL, FACE_THINK, FACE_EYES
    case FACE_SCREAM, FACE_CRY, FACE_STEAM, FACE_CLAP, FACE_MUSCLE, FACE_FIRE

    public var isFace: Bool {
        rawValue.starts(with: "FACE_")
    }

    public var titleArabic: String {
        switch self {
        case .GULP: return "ابلع"
        case .NO_WAY: return "بالله ما طيغ!"
        case .SHEEL: return "شيل"
        case .SPLOOSH: return "سبلوش!"
        case .GHASHANA: return "غشعنا على وجه الخير"
        case .KHATLA: return "خاتلة"
        case .CHAT: return "يلا شباب خلي نسولف"
        case .DRUMROLL: return "طبل"
        case .SAD: return "حزين"
        case .SLAP: return "طگة"
        case .WALLAH: return "والله"
        default: return ""
        }
    }
}

public enum ErrorCode: String, Codable {
    case ROOM_NOT_FOUND
    case ROOM_FULL
    case ROOM_ALREADY_STARTED
    case NOT_HOST
    case NOT_IN_ROOM
    case SEAT_TAKEN
    case INVALID_RULES
    case NOT_ENOUGH_PLAYERS
    case VERSION_MISMATCH
    case BAD_MESSAGE
    case INTERNAL
    case ROOM_LOCKED
    case KICKED

    public var localizedDescriptionArabic: String {
        switch self {
        case .ROOM_NOT_FOUND: return "الغرفة غير موجودة"
        case .ROOM_FULL: return "الغرفة ممتلئة"
        case .ROOM_ALREADY_STARTED: return "المباراة بدأت بالفعل"
        case .NOT_HOST: return "لست مضيف الغرفة"
        case .SEAT_TAKEN: return "هذا المقعد مشغول"
        case .NOT_ENOUGH_PLAYERS: return "اللاعبون غير مكتملين (4 لاعبين)"
        case .ROOM_LOCKED: return "الغرفة مقفلة"
        case .KICKED: return "تم طردك من الغرفة"
        default: return "حدث خطأ في الاتصال"
        }
    }
}

public struct RoomInfo: Codable, Hashable {
    public let code: String
    public let hostId: String
    public var rules: Rules
    public var seats: [Seat]
    public var ready: [String]
    public var started: Bool
    public var spectators: [Seat]
    public var listed: Bool
    public var locked: Bool
    public var teamNames: [String]
    public var tournament: String?
    public var name: String

    public init(
        code: String,
        hostId: String,
        rules: Rules = .default,
        seats: [Seat] = [],
        ready: [String] = [],
        started: Bool = false,
        spectators: [Seat] = [],
        listed: Bool = false,
        locked: Bool = false,
        teamNames: [String] = [],
        tournament: String? = nil,
        name: String = ""
    ) {
        self.code = code
        self.hostId = hostId
        self.rules = rules
        self.seats = seats
        self.ready = ready
        self.started = started
        self.spectators = spectators
        self.listed = listed
        self.locked = locked
        self.teamNames = teamNames
        self.tournament = tournament
        self.name = name
    }
}

public struct PlayerView: Codable, Hashable {
    public let roomCode: String
    public let rules: Rules
    public let seats: [Seat]
    public let yourSeat: Int
    public let hand: [Domino]
    public let handCounts: [Int]
    public let board: Board
    public let turn: Int
    public let actingSeat: Int?
    public let phase: Phase
    public let scores: [Int]
    public let alerts: [Int]
    public let gameNumber: Int
    public let gamesPlayed: Int
    public let mustOpenWith: Domino?
    public let stuckSeat: Int?
    public let rescuer: Int?
    public let teeraSeat: Int?
    public let legalMoves: [Move]
    public let lastGame: GameResult?
    public let losingTeam: Int?
    public let events: [GameEvent]
    public let turnDeadlineEpochMs: Int64?
    public let turnMillisLeft: Int64?
    public let spectating: Bool
    public let spectators: [Seat]

    public var yourTeam: Int { teamOf(yourSeat) }
    public var isYourMove: Bool { !spectating && actingSeat == yourSeat }
    public var handPips: Int { hand.pipTotal() }

    public func seatInfo(_ seat: Int) -> Seat? {
        seats.first { $0.seat == seat }
    }
}
