package com.dominoes.core

import kotlinx.serialization.Serializable

/** Who is allowed to lay the first tile of a round, and with what. */
@Serializable
enum class OpeningRule {
    /** Holder of the highest double must open with it (falls back to heaviest tile). */
    HIGHEST_DOUBLE,

    /** Holder of the single heaviest tile must open with it. */
    HEAVIEST_TILE,

    /** Holder of a specific tile opens with it, e.g. 6|6 in round one. */
    FIXED_TILE,

    /** Seat to the dealer's left opens with any tile they like. */
    DEALER_LEFT_ANY,
}

/** Who opens rounds after the first one. */
@Serializable
enum class SubsequentOpening { SAME_AS_FIRST, PREVIOUS_WINNER, PREVIOUS_LOSER, ROTATE_LEFT }

/** How points are earned. */
@Serializable
enum class ScoringMode {
    /** Points only at the end of a round: the winner takes the losers' spots. */
    PIP_COUNT,

    /** Muggins/All Fives: also score whenever the open ends total a multiple of [RuleSet.pegStep]. */
    PEG_ON_ENDS,
}

/** What happens when the game locks up and nobody can play. */
@Serializable
enum class BlockedResolution {
    /** Lightest hand wins and takes every other hand's spots. */
    LOWEST_PIPS_TAKES_ALL,

    /** Lightest hand wins and takes the difference between the hands. */
    LOWEST_PIPS_TAKES_DIFFERENCE,

    /** Nobody scores; the round is simply redealt. */
    NO_SCORE,
}

/** Whether the match is scored as first-to-target or lowest-after-N-rounds. */
@Serializable
enum class MatchFormat { FIRST_TO_TARGET, FIXED_ROUNDS }

/**
 * Every rule the table plays by, as data.
 *
 * Nothing in the engine hardcodes a variant: "classic draw", "block" and "all fives"
 * are just three points in this space, and a house rule is a fourth. Changing how the
 * game plays should mean changing values here, never engine code.
 */
@Serializable
data class RuleSet(
    val name: String = "Classic Draw",

    // --- table shape -------------------------------------------------------
    val set: DominoSet = DominoSet.DOUBLE_SIX,
    val playerCount: Int = 4,
    /** Seats 0/2 versus 1/3. Requires [playerCount] == 4. */
    val partners: Boolean = true,
    val handSize: Int = 7,

    // --- drawing -----------------------------------------------------------
    /** false = a pure Block game: no boneyard, stuck players simply pass. */
    val boneyardEnabled: Boolean = true,
    /** Tiles that stay in the boneyard and can never be drawn. */
    val boneyardReserve: Int = 0,
    /** true = a stuck player must keep drawing while tiles remain. */
    val mustDrawUntilPlayable: Boolean = true,
    /** 0 = unlimited. Only meaningful when [mustDrawUntilPlayable] is false. */
    val maxDrawsPerTurn: Int = 0,

    // --- opening -----------------------------------------------------------
    val opening: OpeningRule = OpeningRule.HIGHEST_DOUBLE,
    val subsequentOpening: SubsequentOpening = SubsequentOpening.PREVIOUS_WINNER,
    /** Used when [opening] is [OpeningRule.FIXED_TILE]. */
    val openingTile: Domino = Domino(6, 6),

    // --- scoring -----------------------------------------------------------
    val scoring: ScoringMode = ScoringMode.PIP_COUNT,
    /** Peg when the ends total a multiple of this. Set to 3 for an "all threes" house game. */
    val pegStep: Int = 5,
    /** true = pegging 15 scores 3 rather than 15. */
    val pegDividedByStep: Boolean = false,
    /** A double sitting at an end shows both halves, so 5|5 at an end counts 10. */
    val doublesCountBothHalves: Boolean = true,
    /** Round the end-of-round pip count to the nearest multiple of [pegStep]. */
    val roundPipCountToStep: Boolean = false,
    /** Winner takes only the opposing team's spots, not every opponent's. */
    val opposingTeamPipsOnly: Boolean = true,
    val blocked: BlockedResolution = BlockedResolution.LOWEST_PIPS_TAKES_ALL,
    /** Extra points for going out on a double. 0 disables it. */
    val bonusForGoingOutOnDouble: Int = 0,

    // --- match -------------------------------------------------------------
    val matchFormat: MatchFormat = MatchFormat.FIRST_TO_TARGET,
    val targetScore: Int = 101,
    val rounds: Int = 6,
    /** Must land on [targetScore] exactly; overshooting the target does not win. */
    val winMustBeExact: Boolean = false,

    // --- house rules -------------------------------------------------------
    /** 0|0 may be laid on any end and takes that end's value. */
    val doubleBlankIsWild: Boolean = false,
    /** How long a seat has to move before the server plays for them. */
    val turnSeconds: Int = 30,
) {
    val teamCount: Int get() = if (partners) 2 else playerCount

    /** Which score column a seat pegs into. */
    fun scoreKeyOf(seat: Int): Int = if (partners) seat % 2 else seat

    fun seatsFor(scoreKey: Int): List<Int> =
        (0 until playerCount).filter { scoreKeyOf(it) == scoreKey }

    fun validate(): List<String> = buildList {
        if (playerCount !in 2..4) add("playerCount must be between 2 and 4")
        if (partners && playerCount != 4) add("partners requires exactly 4 players")
        if (handSize < 1) add("handSize must be at least 1")
        if (handSize * playerCount + boneyardReserve > set.size) {
            add("${set.name} has ${set.size} tiles, not enough for $playerCount hands of $handSize")
        }
        if (!boneyardEnabled && handSize * playerCount != set.size && playerCount == 4) {
            // legal, just unusual: a block game normally deals the whole pack out
        }
        if (pegStep < 1) add("pegStep must be at least 1")
        if (targetScore < 1) add("targetScore must be at least 1")
        if (opening == OpeningRule.FIXED_TILE && openingTile.high > set.maxFace) {
            add("opening tile $openingTile is not in a ${set.name} pack")
        }
    }

    companion object {
        /** Draw game: boneyard in play, first to 101 by counting the losers' spots. */
        val CLASSIC_DRAW = RuleSet()

        /** Block game: no boneyard, whole pack dealt, stuck players pass. */
        val BLOCK = RuleSet(
            name = "Block",
            boneyardEnabled = false,
            handSize = 7,
            mustDrawUntilPlayable = false,
            blocked = BlockedResolution.LOWEST_PIPS_TAKES_ALL,
        )

        /** All Fives / Muggins: peg during play on multiples of five. */
        val ALL_FIVES = RuleSet(
            name = "All Fives",
            playerCount = 2,
            partners = false,
            scoring = ScoringMode.PEG_ON_ENDS,
            pegStep = 5,
            roundPipCountToStep = true,
            targetScore = 150,
            subsequentOpening = SubsequentOpening.PREVIOUS_WINNER,
        )

        val PRESETS = listOf(CLASSIC_DRAW, BLOCK, ALL_FIVES)
    }
}
