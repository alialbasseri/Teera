package com.teera.core

/**
 * Adds a new state's events to the game's steps so far. A state carries only the latest
 * move's events, sometimes with a call tacked on after it (the same list, longer), so the
 * new ones are whatever it has beyond the state before; a deal starts the list again.
 *
 * [sameList] says the two event lists are known to be the very same report, not just
 * equal: two gives in a row read the same, and only the room can tell them apart.
 */
fun gatherSteps(
    steps: List<GameEvent>,
    before: List<GameEvent>?,
    beforeGame: Int?,
    next: List<GameEvent>,
    nextGame: Int,
    sameList: Boolean = next == before,
): List<GameEvent> {
    val old = before.orEmpty()
    val sameGame = before != null && beforeGame == nextGame
    val fresh = when {
        !sameGame -> next
        sameList -> emptyList()
        next.size > old.size && next.subList(0, old.size) == old -> next.drop(old.size)
        else -> next
    }
    val deal = fresh.indexOfFirst { it is GameEvent.Dealt || it is GameEvent.Redealt }
    return when {
        deal >= 0 -> fresh.drop(deal)
        !sameGame -> fresh
        else -> steps + fresh
    }
}
