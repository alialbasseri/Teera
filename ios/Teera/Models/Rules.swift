import Foundation

public struct Rules: Codable, Equatable, Hashable {
    /// Reaching this score LOSES the match. Table picks 201 or 251 before dealing.
    public var target: Int
    /// 0 leaves seat as long as it likes.
    public var turnSeconds: Int
    public var alertVoteSeconds: Int
    public var alertsPerPenalty: Int
    public var alertPenalty: Int
    /// Emptying your hand takes 25 off your team's score.
    public var xBonus: Int
    /// 0 plays to target. Otherwise match is this many games.
    public var games: Int

    public var byGames: Bool { games > 0 }

    public init(
        target: Int = 201,
        turnSeconds: Int = 0,
        alertVoteSeconds: Int = 30,
        alertsPerPenalty: Int = 3,
        alertPenalty: Int = 50,
        xBonus: Int = -25,
        games: Int = 0
    ) {
        self.target = target
        self.turnSeconds = turnSeconds
        self.alertVoteSeconds = alertVoteSeconds
        self.alertsPerPenalty = alertsPerPenalty
        self.alertPenalty = alertPenalty
        self.xBonus = xBonus
        self.games = games
    }

    public static let targets = [201, 251]
    public static let standardGames = 10
    public static let `default` = Rules()
}
