package com.teera.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.teera.app.R
import com.teera.app.ShownEmote
import com.teera.app.ui.theme.Felt
import com.teera.core.Emote
import kotlinx.coroutines.launch

/** The 3D picture for a face (Fluent Emoji, MIT); null for the soundboard's lines. */
internal fun Emote.faceArt(): Int? = when (this) {
    Emote.FACE_JOY -> R.drawable.face_joy
    Emote.FACE_ROFL -> R.drawable.face_rofl
    Emote.FACE_SMIRK -> R.drawable.face_smirk
    Emote.FACE_COOL -> R.drawable.face_cool
    Emote.FACE_THINK -> R.drawable.face_think
    Emote.FACE_EYES -> R.drawable.face_eyes
    Emote.FACE_SCREAM -> R.drawable.face_scream
    Emote.FACE_CRY -> R.drawable.face_cry
    Emote.FACE_STEAM -> R.drawable.face_steam
    Emote.FACE_CLAP -> R.drawable.face_clap
    Emote.FACE_MUSCLE -> R.drawable.face_muscle
    Emote.FACE_FIRE -> R.drawable.face_fire
    else -> null
}

/**
 * A face thrown onto the table: it springs up from nothing, bobs and rocks while it is
 * there, with a soft shadow under it, then sinks away.
 */
@Composable
internal fun FaceBurst(shown: ShownEmote, modifier: Modifier = Modifier, size: Dp = 76.dp) {
    val art = shown.emote.faceArt() ?: return
    key(shown.id) {
        val pop = remember { Animatable(0f) }
        val lift = remember { Animatable(18f) }
        LaunchedEffect(Unit) {
            launch { lift.animateTo(0f, tween(420, easing = FastOutSlowInEasing)) }
            pop.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMediumLow))
            // Gone just before the table forgets it, so it leaves rather than vanishes.
            kotlinx.coroutines.delay(1_700)
            pop.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
        }
        val motion = rememberInfiniteTransition(label = "face")
        val bob by motion.animateFloat(
            initialValue = -4f, targetValue = 4f,
            animationSpec = infiniteRepeatable(tween(620, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bob",
        )
        val rock by motion.animateFloat(
            initialValue = -9f, targetValue = 9f,
            animationSpec = infiniteRepeatable(tween(760, easing = LinearEasing), RepeatMode.Reverse),
            label = "rock",
        )
        Box(modifier.size(size * 1.25f), contentAlignment = Alignment.Center) {
            // The shadow stays on the felt while the face floats above it.
            Canvas(Modifier.size(size, size * 1.1f)) {
                val scale = pop.value
                drawOval(
                    brush = Brush.radialGradient(
                        listOf(Color.Black.copy(alpha = 0.45f * scale), Color.Transparent),
                        center = Offset(this.size.width / 2, this.size.height * 0.95f),
                        radius = this.size.width * 0.42f,
                    ),
                    topLeft = Offset(this.size.width * 0.12f, this.size.height * 0.84f),
                    size = androidx.compose.ui.geometry.Size(this.size.width * 0.76f, this.size.height * 0.2f),
                )
            }
            Image(
                painterResource(art),
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        val scale = pop.value
                        scaleX = scale
                        scaleY = scale
                        translationY = (bob + lift.value) * density - 6 * density
                        rotationZ = rock * scale
                        alpha = scale.coerceIn(0f, 1f)
                    },
            )
        }
    }
}

/** The faces button beside the soundboard's. */
@Composable
internal fun FacesButton(open: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (open) Felt.Brass else Color(0xFF120B05).copy(alpha = 0.5f))
            .border(1.dp, Felt.Brass.copy(alpha = 0.7f), CircleShape)
            .clickable(onClick = onToggle)
            .testTag("faces"),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.face_joy), contentDescription = "وجوه", modifier = Modifier.size(26.dp))
    }
}

/** Every face, two rows of six; each sits up a little under the thumb. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FacesTray(onFace: (Emote) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        maxItemsInEachRow = 6,
    ) {
        Emote.FACES.forEach { face ->
            val press = remember { MutableInteractionSource() }
            val pressed by press.collectIsPressedAsState()
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (pressed) Felt.Brass.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable(interactionSource = press, indication = null) { onFace(face) }
                    .testTag("face-${face.name}"),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(face.faceArt()!!),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).graphicsLayer {
                        val s = if (pressed) 1.18f else 1f
                        scaleX = s
                        scaleY = s
                    },
                )
            }
        }
    }
}
