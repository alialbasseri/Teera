package com.teera.core

/** Four seats, the human first so bot-driven tests still have somebody to be. */
fun seats(): List<Seat> = (0 until Pack.PLAYERS).map {
    Seat(it, "p$it", "Player ${it + 1}", isBot = it != 0)
}

fun d(a: Int, b: Int): Domino = Domino.of(a, b)

/**
 * A table opened on [centre] with the four sides closed by [arms], in the order
 * north, east, south, west. The open ends are then the far faces of those four tiles.
 */
fun openedBoard(centre: Domino, vararg arms: Domino): Board {
    var board = Board().open(centre)
    arms.forEachIndexed { index, tile -> board = board.place(tile, Arm.ALL[index]) }
    return board
}

/** Builds a table in a known position so one rule can be checked on its own. */
fun rig(
    hands: List<List<Domino>>,
    board: Board = Board(),
    turn: Int = 0,
    phase: Phase = Phase.IN_PLAY,
    scores: List<Int> = listOf(0, 0),
    rules: Rules = Rules.DEFAULT,
    gameNumber: Int = 1,
    opener: Int = 0,
    mustOpenWith: Domino? = null,
    lastToPlay: Int? = null,
    stuckSeat: Int? = null,
    rescuer: Int? = null,
    teeraSeat: Int? = null,
    turnAfterTeera: Int? = null,
    alerts: List<Int> = listOf(0, 0),
) = GameState(
    rules = rules,
    seats = seats(),
    hands = hands,
    board = board,
    turn = turn,
    scores = scores,
    alerts = alerts,
    phase = phase,
    gameNumber = gameNumber,
    opener = opener,
    mustOpenWith = mustOpenWith,
    lastToPlay = lastToPlay,
    stuckSeat = stuckSeat,
    rescuer = rescuer,
    teeraSeat = teeraSeat,
    turnAfterTeera = turnAfterTeera,
)

/**
 * Plays a whole match out with bots at every seat, returning the finished state.
 * [strength] lets a test sit a stronger pair against a weaker one.
 */
fun playOut(
    rules: Rules = Rules.DEFAULT,
    seed: Long,
    strength: (Int) -> Difficulty = { Difficulty.NORMAL },
    onEachMove: (GameState) -> Unit = {},
): GameState {
    var state = Engine.newMatch(rules, seats(), seed)
    var moves = 0
    while (state.phase != Phase.MATCH_OVER && moves < 50_000) {
        if (state.phase == Phase.GAME_OVER) {
            state = Engine.startGame(state)
            continue
        }
        val seat = state.actingSeat ?: error("nobody to act in phase ${state.phase}")
        val move = Bot.chooseMove(state, seat, strength(seat))
            ?: error("seat $seat has no legal move in ${state.phase}")
        val outcome = Engine.apply(state, seat, move)
        check(outcome is MoveOutcome.Ok) { "seed $seed: $move refused: $outcome" }
        state = outcome.state
        onEachMove(state)
        moves++
    }
    return state
}
