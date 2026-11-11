package com.teera.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the launcher icon the way launchers do - the two 108dp layers, cut by a mask
 * that shows the middle 72dp - in a circle and a rounded square, and as a themed icon,
 * on a light and a dark wallpaper. For looking at, like [Screenshots].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class IconPreview {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun layer(id: Int, canvas: Canvas, x: Float, y: Float, size: Float, tint: Int? = null) {
        val drawable = ContextCompat.getDrawable(context, id)!!.mutate()
        tint?.let { drawable.setTint(it) }
        val full = size * 108f / 72f
        val inset = (full - size) / 2f
        drawable.setBounds((x - inset).toInt(), (y - inset).toInt(), (x - inset + full).toInt(), (y - inset + full).toInt())
        drawable.draw(canvas)
    }

    private fun icon(canvas: Canvas, x: Float, y: Float, size: Float, round: Boolean, themed: Boolean = false) {
        val mask = Path().apply {
            if (round) addCircle(x + size / 2, y + size / 2, size / 2, Path.Direction.CW)
            else addRoundRect(x, y, x + size, y + size, size * 0.22f, size * 0.22f, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(mask)
        if (themed) {
            canvas.drawColor(Color.rgb(0xDD, 0xE6, 0xD3))
            layer(R.drawable.ic_launcher_monochrome, canvas, x, y, size, tint = Color.rgb(0x2E, 0x46, 0x2A))
        } else {
            layer(R.drawable.ic_launcher_background, canvas, x, y, size)
            layer(R.drawable.ic_launcher_foreground, canvas, x, y, size)
        }
        canvas.restore()
    }

    @Test
    fun icon() {
        val bitmap = Bitmap.createBitmap(1200, 700, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.color = Color.rgb(0xEC, 0xE7, 0xDF); canvas.drawRect(0f, 0f, 600f, 700f, paint)
        paint.color = Color.rgb(0x1C, 0x1F, 0x26); canvas.drawRect(600f, 0f, 1200f, 700f, paint)
        for (side in 0..1) {
            val ox = side * 600f
            icon(canvas, ox + 40f, 40f, 300f, round = true)
            icon(canvas, ox + 380f, 40f, 180f, round = false)
            icon(canvas, ox + 380f, 260f, 96f, round = true)
            icon(canvas, ox + 500f, 260f, 48f, round = true)
            icon(canvas, ox + 40f, 380f, 180f, round = true, themed = true)
            icon(canvas, ox + 260f, 380f, 180f, round = false)
        }
        val out = File(System.getProperty("teera.screenshots") ?: "build/screenshots").apply { mkdirs() }
        File(out, "icon.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
