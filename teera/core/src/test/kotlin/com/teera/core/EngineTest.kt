package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun seats(n: Int) = (0 until n).map { Seat(it, "p$it", "Player $it", isBot = it != 0) }

private fun state(rules: RuleSet, seed: Long = 7L) =
    Engine.newMatch(rules, seats(rules.playerCount), seed)

/** Puts a known hand and board on the table so a rule can be checked in isolation. */
private fun rigged(
    rules: RuleSet,
    hands: List<List<Domino>>,
    board: Board = Board(),
    turn: Int = 0,
    boneyard: List<Domino> = emptyList(),
) = GameState(
    rules = rules,
    seats = seats(rules.playerCount),
    hands = hands,
    boneyard = boneyard,
    board = board,
    turn = turn,
    scores = List(rules.teamCount) { 0 },
    phase = Phase.IN_ROUND,
    roundNumber = 1,
)

class DominoSetTest {
    @Test
    fun `double six pack has 28 distinct tiles`() {
        val tiles = DominoSet.DOUBLE_SIX.tiles()
        assertEquals(28, tiles.size)
        assertEquals(28, tiles.toSet().size)
        assertEquals(28, DominoSet.DOUBLE_SIX.size)
    }

    @Test
    fun `tiles are canonical either way round`() {
        assertEquals(Domino.of(6, 3), Domino.of(3, 6))
        assertEquals(3, Domino.of(6, 3).otherFace(6))
        assertNull(Domino.of(6, 3).otherFace(5))
    }

    @Test
    fun `heaviest tile sorts last`() {
        assertEquals(Domino(6, 6), DominoSet.DOUBLE_SIX.tiles().max())
    }
}

class BoardTest {
    private val rules = RuleSet.CLASSIC_DRAW

    @Test
    fun `tile is flipped so its matching face touches the line`() {
        val board = Board().place(Domino(3, 6), End.RIGHT, rules)
        assertEquals(3, board.leftEnd)
        assertEquals(6, board.rightEnd)

        val extended = board.place(Domino(1, 6), End.RIGHT, rules)
        assertEquals(3, extended.leftEnd)
        assertEquals(1, extended.rightEnd)

        val both = extended.place(Domino(0, 3), End.LEFT, rules)
        assertEquals(0, both.leftEnd)
        assertEquals(1, both.rightEnd)
    }

    @Test
    fun `non matching tile is refused`() {
        val board = Board().place(Domino(3, 6), End.RIGHT, rules)
        assertFalse(board.accepts(Domino(2, 4), End.LEFT, rules))
        assertTrue(board.accepts(Domino(2, 3), End.LEFT, rules))
    }

    @Test
    fun `lone opening tile shows both faces`() {
        assertEquals(10, Board().place(Domino(5, 5), End.RIGHT, rules).openEndTotal(rules))
        assertEquals(10, Board().place(Domino(4, 6), End.RIGHT, rules).openEndTotal(rules))
    }

    @Test
    fun `a double parked at an end counts both halves`() {
        val board = Board()
            .place(Domino(4, 6), End.RIGHT, rules)
            .place(Domino(4, 4), End.LEFT, rules)
        assertEquals(14, board.openEndTotal(rules))
        assertEquals(10, board.openEndTotal(rules.copy(doublesCountBothHalves = false)))
    }

    @Test
    fun `wild double blank takes the value of the end it is laid on`() {
        val wildRules = rules.copy(doubleBlankIsWild = true)
        val board = Board().place(Domino(3, 6), End.RIGHT, wildRules)
        assertTrue(board.accepts(Domino(0, 0), End.RIGHT, wildRules))
        assertFalse(board.accepts(Domino(0, 0), End.RIGHT, rules))
        assertEquals(6, board.place(Domino(0, 0), End.RIGHT, wildRules).rightEnd)
    }
}

class OpeningTest {
    @Test
    fun `highest double must lead the first round`() {
        val game = state(RuleSet.CLASSIC_DRAW)
        val forced = assertNotNull(game.forcedOpening)
        assertTrue(forced.isDouble)
        assertTrue(forced in game.handOf(game.turn))

        // Nobody else holds a bigger double.
        val biggest = game.hands.flatten().filter { it.isDouble }.max()
        assertEquals(biggest, forced)

        assertEquals(listOf(Move.Play(forced, End.RIGHT)), Engine.legalMoves(game, game.turn))
    }

    @Test
    fun `leading anything else is refused while an opening tile is forced`() {
        val game = state(RuleSet.CLASSIC_DRAW)
        val other = game.handOf(game.turn).first { it != game.forcedOpening }
        val outcome = Engine.apply(game, game.turn, Move.Play(other, End.RIGHT))
        assertEquals(Rejection.MUST_PLAY_OPENING_TILE, assertIs<MoveOutcome.Rejected>(outcome).reason)
    }

    @Test
    fun `heaviest tile rule leads without needing a double`() {
        val rules = RuleSet.CLASSIC_DRAW.copy(opening = OpeningRule.HEAVIEST_TILE)
        val game = state(rules)
        val forced = assertNotNull(game.forcedOpening)
        assertEquals(game.hands.flatten().max(), forced)
    }

    @Test
    fun `dealer's left may open with any tile`() {
        val rules = RuleSet.CLASSIC_DRAW.copy(opening = OpeningRule.DEALER_LEFT_ANY)
        val game = state(rules)
        assertNull(game.forcedOpening)
        assertEquals(1, game.turn)
        assertEquals(game.handOf(1).size, Engine.legalMoves(game, 1).size)
    }
}

class TurnRulesTest {
    private val rules = RuleSet.CLASSIC_DRAW

    @Test
    fun `a player who can play may not draw or pass`() {
        val game = rigged(
            rules,
            hands = listOf(listOf(Domino(3, 4)), listOf(Domino(1, 1)), listOf(Domino(2, 2)), listOf(Domino(5, 5))),
            board = Board().place(Domino(3, 6), End.RIGHT, rules),
            boneyard = listOf(Domino(0, 0)),
        )
        assertEquals(Rejection.MUST_PLAY_IF_ABLE, assertIs<MoveOutcome.Rejected>(Engine.apply(game, 0, Move.Draw)).reason)
        assertEquals(Rejection.MUST_PLAY_IF_ABLE, assertIs<MoveOutcome.Rejected>(Engine.apply(game, 0, Move.Pass)).reason)
    }

    @Test
    fun `a stuck player must draw while the boneyard holds tiles`() {
        val game = rigged(
            rules,
            hands = listOf(listOf(Domino(1, 2)), listOf(Domino(1, 1)), listOf(Domino(2, 2)), listOf(Domino(5, 5))),
            board = Board().place(Domino(3, 6), End.RIGHT, rules),
            boneyard = listOf(Domino(0, 3)),
        )
        assertEquals(listOf(Move.Draw), Engine.legalMoves(game, 0))
        assertEquals(Rejection.MUST_DRAW_FIRST, assertIs<MoveOutcome.Rejected>(Engine.apply(game, 0, Move.Pass)).reason)

        val drawn = assertIs<MoveOutcome.Ok>(Engine.apply(game, 0, Move.Draw)).state
        assertEquals(2, drawn.handOf(0).size)
        assertTrue(drawn.boneyard.isEmpty())
        assertEquals(0, drawn.turn, "drawing does not end the turn")
        assertEquals(listOf(Move.Play(Domino(0, 3), End.LEFT)), Engine.legalMoves(drawn, 0))
    }

    @Test
    fun `pass is only legal once the boneyard is exhausted`() {
        val game = rigged(
            rules,
            hands = listOf(listOf(Domino(1, 2)), listOf(Domino(1, 1)), listOf(Domino(2, 2)), listOf(Domino(5, 5))),
            board = Board().place(Domino(3, 6), End.RIGHT, rules),
        )
        assertEquals(listOf(Move.Pass), Engine.legalMoves(game, 0))
        val passed = assertIs<MoveOutcome.Ok>(Engine.apply(game, 0, Move.Pass)).state
        assertEquals(1, passed.turn)
        assertEquals(1, passed.consecutivePasses)
    }

    @Test
    fun `only the seat on turn may move`() {
        val game = state(RuleSet.CLASSIC_DRAW)
        val offTurn = (game.turn + 1) % 4
        val outcome = Engine.apply(game, offTurn, Move.Pass)
        assertEquals(Rejection.NOT_YOUR_TURN, assertIs<MoveOutcome.Rejected>(outcome).reason)
    }

    @Test
    fun `reserved tiles stay in the boneyard`() {
        val reserved = rules.copy(boneyardReserve = 2)
        val game = rigged(
            reserved,
            hands = listOf(listOf(Domino(1, 2)), listOf(Domino(1, 1)), listOf(Domino(2, 2)), listOf(Domino(5, 5))),
            board = Board().place(Domino(3, 6), End.RIGHT, reserved),
            boneyard = listOf(Domino(0, 4), Domino(0, 5)),
        )
        assertEquals(listOf(Move.Pass), Engine.legalMoves(game, 0))
    }
}

class ScoringTest {
    @Test
    fun `going out scores the opposing team's spots`() {
        val rules = RuleSet.CLASSIC_DRAW
        val game = rigged(
            rules,
            hands = listOf(
                listOf(Domino(3, 4)),
                listOf(Domino(1, 1)),   // 2
                listOf(Domino(6, 6)),   // partner, must not count
                listOf(Domino(2, 5)),   // 7
            ),
            board = Board().place(Domino(3, 6), End.RIGHT, rules),
        )
        val done = assertIs<MoveOutcome.Ok>(Engine.apply(game, 0, Move.Play(Domino(3, 4), End.LEFT))).state
        val result = assertNotNull(done.lastRound)

        assertEquals(RoundEndReason.DOMINO, result.reason)
        assertEquals(0, result.winnerSeat)
        assertEquals(9, result.points)
        assertEquals(9, done.scores[0])
        assertEquals(0, done.scores[1])
        assertEquals(Phase.ROUND_OVER, done.phase)
    }

    @Test
    fun `counting every opponent includes the partner`() {
        val rules = RuleSet.CLASSIC_DRAW.copy(opposingTeamPipsOnly = false)
        val game = rigged(
            rules,
            hands = listOf(listOf(Domino(3, 4)), listOf(Domino(1, 1)), listOf(Domino(6, 6)), listOf(Domino(2, 5))),
            board = Board().place(Domino(3, 6), End.RIGHT, rules),
        )
        val done = assertIs<MoveOutcome.Ok>(Engine.apply(game, 0, Move.Play(Domino(3, 4), End.LEFT))).state
        assertEquals(21, assertNotNull(done.lastRound).points)
    }

    @Test
    fun `a blocked round goes to the lightest hand`() {
        val rules = RuleSet.BLOCK
        val game = rigged(
            rules,
            hands = listOf(
                listOf(Domino(1, 2)),   // 3
                listOf(Domino(1, 1)),   // 2
                listOf(Domino(2, 2)),   // 4
                listOf(Domino(5, 5)),   // 10
            ),
            board = Board().place(Domino(3, 6), End.RIGHT, rules),
        )
        var current = game
        repeat(4) { seat ->
            current = assertIs<MoveOutcome.Ok>(Engine.apply(current, seat, Move.Pass)).state
        }
        val result = assertNotNull(current.lastRound)
        assertEquals(RoundEndReason.BLOCKED, result.reason)
        // team 0/2 = 3 + 4 = 7 beats team 1/3 = 2 + 10 = 12; seat 0 is the lighter of the pair.
        assertEquals(0, result.winnerSeat)
        assertEquals(12, result.points)
    }

    @Test
    fun `blocked round can be scored on the difference instead`() {
        val rules = RuleSet.BLOCK.copy(blocked = BlockedResolution.LOWEST_PIPS_TAKES_DIFFERENCE)
        val game = rigged(
            rules,
            hands = listOf(listOf(Domino(1, 2)), listOf(Domino(1, 1)), listOf(Domino(2, 2)), listOf(Domino(5, 5))),
            board = Board().place(Domino(3, 6), End.RIGHT, rules),
        )
        var current = game
        repeat(4) { seat -> current = assertIs<MoveOutcome.Ok>(Engine.apply(current, seat, Move.Pass)).state }
        val result = assertNotNull(current.lastRound)
        // team 0/2 = 3 + 4 = 7, team 1/3 = 2 + 10 = 12, so 0/2 takes 12 - 7.
        assertEquals(5, result.points)
    }

    @Test
    fun `a blocked round can be worth nothing`() {
        val rules = RuleSet.BLOCK.copy(blocked = BlockedResolution.NO_SCORE)
        val game = rigged(
            rules,
            hands = listOf(listOf(Domino(1, 2)), listOf(Domino(1, 1)), listOf(Domino(2, 2)), listOf(Domino(5, 5))),
            board = Board().place(Domino(3, 6), End.RIGHT, rules),
        )
        var current = game
        repeat(4) { seat -> current = assertIs<MoveOutcome.Ok>(Engine.apply(current, seat, Move.Pass)).state }
        val result = assertNotNull(current.lastRound)
        assertNull(result.winnerSeat)
        assertEquals(0, result.points)
        assertEquals(listOf(0, 0), current.scores)
    }

    @Test
    fun `all fives pegs whenever the ends land on a multiple`() {
        val rules = RuleSet.ALL_FIVES
        // Each hand keeps a spare tile so going out does not end the round mid-test.
        val spare = Domino(0, 0)

        val miss = rigged(
            rules,
            hands = listOf(listOf(Domino(4, 6), spare), listOf(Domino(2, 2))),
            board = Board().place(Domino(6, 6), End.RIGHT, rules),
        )
        val after = assertIs<MoveOutcome.Ok>(Engine.apply(miss, 0, Move.Play(Domino(4, 6), End.RIGHT))).state
        // 6|6 shows both halves at the left end, plus the 4 at the right: 16, no peg.
        assertEquals(0, after.scores[0])

        val hit = rigged(
            rules,
            hands = listOf(listOf(Domino(5, 6), spare), listOf(Domino(2, 2))),
            board = Board().place(Domino(4, 6), End.RIGHT, rules),
        )
        val scored = assertIs<MoveOutcome.Ok>(Engine.apply(hit, 0, Move.Play(Domino(5, 6), End.RIGHT))).state
        assertEquals(0, scored.scores[0], "4 + 5 = 9 is not a multiple of five")

        val ten = rigged(
            rules,
            hands = listOf(listOf(Domino(1, 6), spare), listOf(Domino(2, 2))),
            board = Board().place(Domino(4, 6), End.RIGHT, rules),
        )
        // 4 at one end, the 6 covered by 6|1 leaving 1: total 5.
        val pegged = assertIs<MoveOutcome.Ok>(Engine.apply(ten, 0, Move.Play(Domino(1, 6), End.RIGHT))).state
        assertEquals(5, pegged.scores[0])
        assertTrue(pegged.events.any { it is GameEvent.Pegged })
    }

    @Test
    fun `peg step is a house rule, not a constant`() {
        val threes = RuleSet.ALL_FIVES.copy(pegStep = 3, name = "All Threes")
        val spare = Domino(0, 0)

        val fiveOnThrees = rigged(
            threes,
            hands = listOf(listOf(Domino(1, 6), spare), listOf(Domino(2, 2))),
            board = Board().place(Domino(4, 6), End.RIGHT, threes),
        )
        // Ends of 4 and 1 total 5, which pegs on fives but not on threes.
        val a = assertIs<MoveOutcome.Ok>(Engine.apply(fiveOnThrees, 0, Move.Play(Domino(1, 6), End.RIGHT))).state
        assertEquals(0, a.scores[0])

        val sixOnThrees = rigged(
            threes,
            hands = listOf(listOf(Domino(2, 6), spare), listOf(Domino(2, 2))),
            board = Board().place(Domino(4, 6), End.RIGHT, threes),
        )
        val b = assertIs<MoveOutcome.Ok>(Engine.apply(sixOnThrees, 0, Move.Play(Domino(2, 6), End.RIGHT))).state
        assertEquals(6, b.scores[0], "4 + 2 = 6 pegs under an all-threes house rule")

        val divided = threes.copy(pegDividedByStep = true)
        val counted = rigged(
            divided,
            hands = listOf(listOf(Domino(2, 6), spare), listOf(Domino(2, 2))),
            board = Board().place(Domino(4, 6), End.RIGHT, divided),
        )
        val c = assertIs<MoveOutcome.Ok>(Engine.apply(counted, 0, Move.Play(Domino(2, 6), End.RIGHT))).state
        assertEquals(2, c.scores[0], "tables that score in threes peg 6 as two")
    }

    @Test
    fun `pip counts can be rounded to the nearest five`() {
        val rules = RuleSet.CLASSIC_DRAW.copy(roundPipCountToStep = true, opposingTeamPipsOnly = false)
        val game = rigged(
            rules,
            hands = listOf(listOf(Domino(3, 4)), listOf(Domino(1, 1)), listOf(Domino(0, 1)), listOf(Domino(2, 5))),
            board = Board().place(Domino(3, 6), End.RIGHT, rules),
        )
        val done = assertIs<MoveOutcome.Ok>(Engine.apply(game, 0, Move.Play(Domino(3, 4), End.LEFT))).state
        // 2 + 1 + 7 = 10 exactly.
        assertEquals(10, assertNotNull(done.lastRound).points)
    }

    @Test
    fun `going out on a double can carry a bonus`() {
        val rules = RuleSet.CLASSIC_DRAW.copy(bonusForGoingOutOnDouble = 25)
        val game = rigged(
            rules,
            hands = listOf(listOf(Domino(3, 3)), listOf(Domino(1, 1)), listOf(Domino(6, 6)), listOf(Domino(2, 5))),
            board = Board().place(Domino(3, 6), End.RIGHT, rules),
        )
        val done = assertIs<MoveOutcome.Ok>(Engine.apply(game, 0, Move.Play(Domino(3, 3), End.LEFT))).state
        assertEquals(9 + 25, assertNotNull(done.lastRound).points)
    }
}

class MatchTest {
    @Test
    fun `reaching the target ends the match`() {
        val rules = RuleSet.CLASSIC_DRAW.copy(targetScore = 5)
        val game = rigged(
            rules,
            hands = listOf(listOf(Domino(3, 4)), listOf(Domino(1, 1)), listOf(Domino(6, 6)), listOf(Domino(2, 5))),
            board = Board().place(Domino(3, 6), End.RIGHT, rules),
        )
        val done = assertIs<MoveOutcome.Ok>(Engine.apply(game, 0, Move.Play(Domino(3, 4), End.LEFT))).state
        assertEquals(Phase.MATCH_OVER, done.phase)
        assertEquals(0, done.matchWinner)
        assertTrue(done.events.any { it is GameEvent.MatchEnded })
    }

    @Test
    fun `an exact-finish table refuses to score an overshoot`() {
        val rules = RuleSet.CLASSIC_DRAW.copy(targetScore = 5, winMustBeExact = true)
        val game = rigged(
            rules,
            hands = listOf(listOf(Domino(3, 4)), listOf(Domino(1, 1)), listOf(Domino(6, 6)), listOf(Domino(2, 5))),
            board = Board().place(Domino(3, 6), End.RIGHT, rules),
        )
        val done = assertIs<MoveOutcome.Ok>(Engine.apply(game, 0, Move.Play(Domino(3, 4), End.LEFT))).state
        assertEquals(listOf(0, 0), done.scores, "9 would overshoot 5, so it is not pegged")
        assertEquals(Phase.ROUND_OVER, done.phase)
        assertNull(done.matchWinner)
    }

    @Test
    fun `the round winner leads the next one`() {
        val rules = RuleSet.CLASSIC_DRAW.copy(targetScore = 500)
        val game = rigged(
            rules,
            hands = listOf(listOf(Domino(3, 4)), listOf(Domino(1, 1)), listOf(Domino(6, 6)), listOf(Domino(2, 5))),
            board = Board().place(Domino(3, 6), End.RIGHT, rules),
        )
        val done = assertIs<MoveOutcome.Ok>(Engine.apply(game, 0, Move.Play(Domino(3, 4), End.LEFT))).state
        val next = Engine.startRound(done)
        assertEquals(0, next.turn)
        assertNull(next.forcedOpening, "a returning winner leads with whatever they like")
        assertEquals(2, next.roundNumber)
        assertEquals(rules.handSize, next.handOf(0).size)
    }
}

class FullGameTest {
    /** Plays whole matches out with a bot at every seat: no rule may deadlock or throw. */
    @Test
    fun `every preset plays to a finish`() {
        for (preset in RuleSet.PRESETS) {
            for (seed in 1L..25L) {
                var game = Engine.newMatch(preset, seats(preset.playerCount), seed)
                var moves = 0
                while (game.phase != Phase.MATCH_OVER && moves < 20_000) {
                    if (game.phase == Phase.ROUND_OVER) {
                        game = Engine.startRound(game)
                        continue
                    }
                    val move = Bot.chooseMove(game, game.turn)
                    assertNotNull(move, "${preset.name}: seat ${game.turn} had no legal move")
                    game = assertIs<MoveOutcome.Ok>(
                        Engine.apply(game, game.turn, move),
                        "${preset.name} seed $seed rejected $move",
                    ).state
                    moves++
                }
                assertEquals(Phase.MATCH_OVER, game.phase, "${preset.name} seed $seed never finished")
                assertNotNull(game.matchWinner)
                assertTrue(game.scores.max() >= preset.targetScore || preset.matchFormat == MatchFormat.FIXED_ROUNDS)
            }
        }
    }

    @Test
    fun `tiles are conserved for the whole match`() {
        var game = Engine.newMatch(RuleSet.CLASSIC_DRAW, seats(4), 42L)
        val packSize = RuleSet.CLASSIC_DRAW.set.size
        var moves = 0
        while (game.phase != Phase.MATCH_OVER && moves < 20_000) {
            if (game.phase == Phase.ROUND_OVER) {
                game = Engine.startRound(game)
                continue
            }
            val onTable = game.hands.flatten() + game.boneyard + game.board.tiles.map { it.domino }
            assertEquals(packSize, onTable.size, "a tile went missing or was duplicated")
            assertEquals(packSize, onTable.toSet().size, "a tile is on the table twice")

            val move = assertNotNull(Bot.chooseMove(game, game.turn))
            game = assertIs<MoveOutcome.Ok>(Engine.apply(game, game.turn, move)).state
            moves++
        }
    }

    @Test
    fun `a seeded match replays identically`() {
        fun play(seed: Long): List<Int> {
            var game = Engine.newMatch(RuleSet.CLASSIC_DRAW, seats(4), seed)
            var moves = 0
            while (game.phase != Phase.MATCH_OVER && moves < 20_000) {
                if (game.phase == Phase.ROUND_OVER) { game = Engine.startRound(game); continue }
                val move = assertNotNull(Bot.chooseMove(game, game.turn))
                game = assertIs<MoveOutcome.Ok>(Engine.apply(game, game.turn, move)).state
                moves++
            }
            return game.scores
        }
        assertEquals(play(99L), play(99L))
    }
}
