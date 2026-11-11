package com.teera.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.teera.app.ui.theme.Felt
import com.teera.core.RoomInfo

/** Talking at the table: the state of this phone's mic, and who is speaking right now. */
data class Talk(
    val micOn: Boolean = false,
    /** This phone is sending, i.e. its player is actually talking. */
    val talking: Boolean = false,
    /** Off when this player has muted everybody else. */
    val hearing: Boolean = true,
    /** Seats whose voices are playing. */
    val speaking: Set<Int> = emptySet(),
    val onMic: () -> Unit = {},
    val onHearing: () -> Unit = {},
)

/**
 * The microphone switch: lapis and struck through when off, gold when open, and a green
 * ring breathing round it while this player's voice is going out.
 */
@Composable
fun MicButton(talk: Talk, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(modifier.size(size + 10.dp), contentAlignment = Alignment.Center) {
        if (talk.micOn && talk.talking) SpeakingHalo(size + 8.dp)
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    if (talk.micOn) Brush.verticalGradient(listOf(Felt.GoldLit, Felt.Brass, Felt.GoldDeep))
                    else Brush.verticalGradient(listOf(Felt.Lapis, Felt.Lapis)),
                )
                .border(1.dp, Felt.Brass.copy(alpha = if (talk.micOn) 1f else 0.6f), CircleShape)
                .clickable(onClick = talk.onMic)
                .testTag("mic"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (talk.micOn) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                contentDescription = if (talk.micOn) "سد المايك" else "افتح المايك",
                tint = if (talk.micOn) Felt.Ink else Felt.Muted,
            )
        }
    }
}

/** The green ring round whoever is talking. Drawn behind an avatar of the same centre. */
@Composable
fun SpeakingHalo(size: Dp, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "talk")
    val grow by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "grow",
    )
    Box(
        modifier
            .size(size)
            .scale(grow)
            // Kept inside 0..1 whatever the animation does; see drawRosette for why it matters.
            .alpha((1.9f - grow).coerceIn(0f, 1f))
            .border(3.dp, Felt.Playable, CircleShape),
    )
}

/** Seats to ring in the lobby: everybody heard, and this player while they talk. */
internal fun Talk?.speakingSeats(myId: String, room: RoomInfo): Set<Int> {
    if (this == null) return emptySet()
    val mine = room.seats.firstOrNull { it.playerId == myId }?.seat
    return if (talking && mine != null) speaking + mine else speaking
}
