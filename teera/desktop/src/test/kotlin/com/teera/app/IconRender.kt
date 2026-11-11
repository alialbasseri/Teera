package com.teera.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import org.xml.sax.InputSource

/** Renders the phone's launcher icon (both layers) at 1024 px, for the iPhone app: -Pteera.icon=<out.png> */
class IconRender {
    @Test
    fun render() {
        val out = System.getProperty("teera.icon")?.takeIf { it.isNotBlank() } ?: return
        val res = File(System.getProperty("teera.res")!!)
        val density = Density(1f)
        val layers = listOf("ic_launcher_background", "ic_launcher_foreground").map {
            File(res, "drawable/$it.xml").inputStream().use { input -> loadXmlImageVector(InputSource(input), density) }
        }
        val scene = ImageComposeScene(1024, 1024, density) {
            Box(Modifier.fillMaxSize()) {
                layers.forEach { Image(rememberVectorPainter(it), null, Modifier.fillMaxSize()) }
            }
        }
        val image = scene.render()
        ImageIO.write(image.toComposeImageBitmap().toAwtImage(), "png", File(out))
        scene.close()
    }
}

