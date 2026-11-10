import Foundation

public final class Engine {

    public static func legalMoves(for state: PlayerView) -> [Move] {
        return state.legalMoves
    }

    /// Evaluates legal moves for a local game state
    public static func computeLegalMoves(
        board: Board,
        hand: [Domino],
        phase: Phase,
        mustOpenWith: Domino?,
        isActing: Bool
    ) -> [Move] {
        guard isActing else { return [] }

        switch phase {
        case .REDEAL_OFFER:
            return [.redeal, .keepHand]

        case .TEERA_OFFER:
            return [.teera, .carryOn]

        case .RESCUE:
            var moves: [Move] = []
            for tile in hand {
                moves.append(.give(tile))
                let arms = board.armsAccepting(tile)
                for arm in arms {
                    moves.append(.playFor(tile, arm: arm))
                }
            }
            return moves

        case .IN_PLAY:
            if board.isEmpty {
                if let required = mustOpenWith {
                    return hand.contains(required) ? [.play(required)] : []
                }
                return hand.filter { $0.isDouble }.map { .play($0) }
            }

            var moves: [Move] = []
            for tile in hand {
                let arms = board.armsAccepting(tile)
                for arm in arms {
                    moves.append(.play(tile, arm: arm))
                }
            }
            return moves

        case .GAME_OVER, .MATCH_OVER:
            return []
        }
    }
}
