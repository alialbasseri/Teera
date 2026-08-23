package com.dominoes.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.dominoes.app.ui.theme.Felt
import com.dominoes.core.BlockedResolution
import com.dominoes.core.OpeningRule
import com.dominoes.core.RuleSet
import com.dominoes.core.ScoringMode

/**
 * Where the house rules get argued over.
 *
 * Everything here writes straight into [RuleSet], which is the only thing the engine
 * reads, so a switch on this screen genuinely changes how the game plays rather than
 * toggling a special case somewhere.
 */
@Composable
fun RulesScreen(
    rules: RuleSet,
    editable: Boolean,
    onChange: (RuleSet) -> Unit,
    onDone: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Felt.TableLight, Felt.Deep)))
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Column {
                Spacer(Modifier.height(20.dp))
                Text("House rules", style = MaterialTheme.typography.titleLarge, color = Felt.Parchment)
                Text(
                    if (editable) "The host sets these before the deal" else "Set by the host",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Felt.IvoryShade,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuleSet.PRESETS.forEach { preset ->
                        AssistChip(
                            onClick = { if (editable) onChange(preset) },
                            label = { Text(preset.name) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (rules.name == preset.name) Felt.Brass else Felt.Deep,
                                labelColor = if (rules.name == preset.name) Felt.Ink else Felt.Parchment,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        section("The table") {
            Stepper("Players", rules.playerCount, 2..4, editable) {
                onChange(rules.copy(playerCount = it, partners = it == 4 && rules.partners).renamed())
            }
            SwitchRow("Partners (seats across)", rules.partners, editable && rules.playerCount == 4) {
                onChange(rules.copy(partners = it).renamed())
            }
            Stepper("Tiles dealt", rules.handSize, 5..12, editable) {
                onChange(rules.copy(handSize = it).renamed())
            }
        }

        section("Drawing") {
            SwitchRow("Boneyard in play", rules.boneyardEnabled, editable) {
                onChange(rules.copy(boneyardEnabled = it, mustDrawUntilPlayable = it).renamed())
            }
            SwitchRow("Must draw until playable", rules.mustDrawUntilPlayable, editable && rules.boneyardEnabled) {
                onChange(rules.copy(mustDrawUntilPlayable = it).renamed())
            }
            Stepper("Tiles kept back", rules.boneyardReserve, 0..4, editable && rules.boneyardEnabled) {
                onChange(rules.copy(boneyardReserve = it).renamed())
            }
        }

        section("Opening") {
            Choice(
                label = "Who leads",
                options = OpeningRule.entries.map { it to it.label() },
                selected = rules.opening,
                editable = editable,
            ) { onChange(rules.copy(opening = it).renamed()) }
        }

        section("Scoring") {
            Choice(
                label = "Points",
                options = ScoringMode.entries.map { it to it.label() },
                selected = rules.scoring,
                editable = editable,
            ) { onChange(rules.copy(scoring = it).renamed()) }

            if (rules.scoring == ScoringMode.PEG_ON_ENDS) {
                Stepper("Score on multiples of", rules.pegStep, 3..6, editable) {
                    onChange(rules.copy(pegStep = it).renamed())
                }
                SwitchRow("Count in units, not spots", rules.pegDividedByStep, editable) {
                    onChange(rules.copy(pegDividedByStep = it).renamed())
                }
            }

            SwitchRow("Doubles at an end count twice", rules.doublesCountBothHalves, editable) {
                onChange(rules.copy(doublesCountBothHalves = it).renamed())
            }
            SwitchRow("Round the count to the nearest five", rules.roundPipCountToStep, editable) {
                onChange(rules.copy(roundPipCountToStep = it).renamed())
            }
            if (rules.partners) {
                SwitchRow("Count only the other team", rules.opposingTeamPipsOnly, editable) {
                    onChange(rules.copy(opposingTeamPipsOnly = it).renamed())
                }
            }
            Choice(
                label = "When the game blocks",
                options = BlockedResolution.entries.map { it to it.label() },
                selected = rules.blocked,
                editable = editable,
            ) { onChange(rules.copy(blocked = it).renamed()) }
            Stepper("Bonus for going out on a double", rules.bonusForGoingOutOnDouble, 0..50, editable, step = 5) {
                onChange(rules.copy(bonusForGoingOutOnDouble = it).renamed())
            }
        }

        section("The match") {
            Stepper("Play to", rules.targetScore, 50..250, editable, step = 25) {
                onChange(rules.copy(targetScore = it).renamed())
            }
            SwitchRow("Must land on the target exactly", rules.winMustBeExact, editable) {
                onChange(rules.copy(winMustBeExact = it).renamed())
            }
            Stepper("Seconds per turn", rules.turnSeconds, 0..60, editable, step = 5) {
                onChange(rules.copy(turnSeconds = it).renamed())
            }
        }

        section("House extras") {
            SwitchRow("Double blank plays as a wild", rules.doubleBlankIsWild, editable) {
                onChange(rules.copy(doubleBlankIsWild = it).renamed())
            }
        }

        item {
            Column {
                val problems = rules.validate()
                if (problems.isNotEmpty()) {
                    Text(
                        problems.joinToString("\n"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Felt.Danger,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onDone,
                    enabled = problems.isEmpty(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Felt.Brass, contentColor = Felt.Ink),
                ) {
                    Text("Done", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

/** Anything the player edits stops claiming to be a stock variant. */
private fun RuleSet.renamed(): RuleSet =
    if (RuleSet.PRESETS.any { it == this }) this else copy(name = "House rules")

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    content: @Composable () -> Unit,
) {
    item {
        Column {
            Spacer(Modifier.height(14.dp))
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Felt.Brass)
            HorizontalDivider(color = Felt.Brass.copy(alpha = 0.3f))
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, editable: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = Felt.Parchment, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = { if (editable) onChange(it) },
            enabled = editable,
            colors = SwitchDefaults.colors(checkedTrackColor = Felt.Brass, checkedThumbColor = Felt.Ink),
        )
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    editable: Boolean,
    step: Int = 1,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = Felt.Parchment, style = MaterialTheme.typography.bodyLarge)
        IconButton(
            onClick = { onChange((value - step).coerceIn(range)) },
            enabled = editable && value > range.first,
        ) { Text("−", color = Felt.Brass, style = MaterialTheme.typography.titleLarge) }
        Text(
            value.toString(),
            Modifier.width(44.dp),
            color = Felt.Parchment,
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(
            onClick = { onChange((value + step).coerceIn(range)) },
            enabled = editable && value < range.last,
        ) { Text("+", color = Felt.Brass, style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun <T> Choice(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    editable: Boolean,
    onChange: (T) -> Unit,
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, color = Felt.Parchment, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (option, text) ->
                AssistChip(
                    onClick = { if (editable) onChange(option) },
                    label = { Text(text) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (option == selected) Felt.Brass else Felt.Deep,
                        labelColor = if (option == selected) Felt.Ink else Felt.Parchment,
                    ),
                )
            }
        }
    }
}

private fun OpeningRule.label(): String = when (this) {
    OpeningRule.HIGHEST_DOUBLE -> "Highest double leads"
    OpeningRule.HEAVIEST_TILE -> "Heaviest bone leads"
    OpeningRule.FIXED_TILE -> "Double six leads"
    OpeningRule.DEALER_LEFT_ANY -> "Dealer's left, any bone"
}

private fun ScoringMode.label(): String = when (this) {
    ScoringMode.PIP_COUNT -> "Count the losers' spots"
    ScoringMode.PEG_ON_ENDS -> "Score the ends as you play"
}

private fun BlockedResolution.label(): String = when (this) {
    BlockedResolution.LOWEST_PIPS_TAKES_ALL -> "Lightest hand takes the lot"
    BlockedResolution.LOWEST_PIPS_TAKES_DIFFERENCE -> "Lightest hand takes the difference"
    BlockedResolution.NO_SCORE -> "Nobody scores, deal again"
}

/** One-line description used on the lobby card. */
fun rulesSummary(rules: RuleSet): String = buildList {
    add("${rules.playerCount} players")
    if (rules.partners) add("partners")
    add("${rules.handSize} bones")
    add(if (rules.boneyardEnabled) "draw" else "block")
    if (rules.scoring == ScoringMode.PEG_ON_ENDS) add("score on ${rules.pegStep}s")
    add("to ${rules.targetScore}")
    if (rules.doubleBlankIsWild) add("wild blank")
}.joinToString(" · ")
