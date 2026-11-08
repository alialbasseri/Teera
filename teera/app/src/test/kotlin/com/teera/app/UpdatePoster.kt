package com.teera.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teera.app.ui.CafeTable
import com.teera.app.ui.DominoTile
import com.teera.app.ui.PresetAvatar
import com.teera.app.ui.RosetteRule
import com.teera.app.ui.TileBacks
import com.teera.app.ui.drawRosette
import com.teera.app.ui.theme.Felt
import com.teera.app.ui.theme.TeeraTheme
import com.teera.core.Domino
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The picture for the group when an update is out: what is new, and where to get it.
 * 1080 by 1350, like the teasers.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h450dp-xxhdpi")
class UpdatePoster {

    @get:Rule
    val compose = createComposeRule()

    private val out = File(System.getProperty("teera.teasers") ?: "build/teasers").apply { mkdirs() }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { TeeraTheme(dark = true) { content() } }
        repeat(30) {
            compose.mainClock.advanceTimeBy(100)
            shadowOf(Looper.getMainLooper()).idle()
        }
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val view = (global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<View>)
            .first { it.width > 0 }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("TEASER=${File(out, "$name.png").absolutePath} ${bitmap.width}x${bitmap.height}")
    }


    private val bones = listOf(
        com.teera.app.ui.Lying(Domino(6, 6), 0.86f, 0.03f, 24f),
        com.teera.app.ui.Lying(Domino(0, 1), -0.04f, 0.05f, -30f),
        com.teera.app.ui.Lying(Domino(2, 6), -0.03f, 0.93f, -64f),
        com.teera.app.ui.Lying(null, 0.92f, 0.95f, 96f),
    )

    @Test
    fun update141() = shoot("update-1.4.1") {
        CafeTable(bones = bones, cups = emptyList()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("طيرة", color = Felt.Brass, fontSize = 40.sp, style = MaterialTheme.typography.displayLarge)
                Text(
                    "نزل تحديث جديد! (1.4.1)",
                    color = Felt.Parchment,
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(4.dp))
                RosetteRule(Modifier.padding(horizontal = 70.dp))
                Spacer(Modifier.height(6.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .border(1.dp, Felt.Brass.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    listOf(
                        "إذا انقطع النت ترجع لكرسيك لاعب",
                        "المتفرج يلعب بدال اللي انقطع",
                        "غيّر النقاط ٢٠١ ← ٢٥١ إذا الكل وافق",
                        "أصوات: سبلوش، والله دنيييكم",
                        "كبّر الطاولة بإصبعين",
                        "ومن هسه، التحديث يجيك بزر واحد",
                    ).forEach { line ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 0.dp)) {
                            Text("✓", color = Felt.Playable, fontSize = 15.sp)
                            Spacer(Modifier.size(8.dp))
                            Text(line, color = Felt.Parchment, fontSize = 13.sp, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                        .background(Brush.verticalGradient(listOf(Felt.GoldLit, Felt.Brass)))
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                ) {
                    Text("نزّله من هنا ⬇", color = Felt.Ink, fontSize = 18.sp, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "teera-xfvr.onrender.com/download",
                    color = Felt.GoldLit,
                    fontSize = 14.sp,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "للأندرويد · ينزل فوگ القديمة وتبقى بياناتك",
                    color = Felt.Muted,
                    fontSize = 12.sp,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
