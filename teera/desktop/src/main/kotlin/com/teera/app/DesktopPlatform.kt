package com.teera.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/*
 * What a computer does its own way: the desktop's answers to the questions the phone's
 * Platform.kt answers for Android. Everything else is the phone's code, shared.
 */

typealias Picture = BufferedImage

/** The app as a whole: where it keeps its files and settings. */
class DesktopApp(val dataDir: File) {
    val prefs = DesktopPrefs(File(dataDir, "settings.properties"))

    companion object {
        /** %APPDATA%\Teera on Windows, ~/.teera elsewhere. */
        fun create(): DesktopApp {
            val base = System.getenv("APPDATA")?.let { File(it, "Teera") }
                ?: File(System.getProperty("user.home"), ".teera")
            base.mkdirs()
            return DesktopApp(base)
        }
    }
}

typealias PlatformApp = DesktopApp

/** A picked photo: a file on the computer. */
typealias PhotoSource = File

object AppInfo {
    const val VERSION_CODE = BuildInfo.VERSION_CODE
    const val VERSION_NAME = BuildInfo.VERSION_NAME
    const val SERVER_URL = BuildInfo.SERVER_URL
    const val PLATFORM = "windows"

    /** Whether this app can play a table on the Wi-Fi (the menu's «وايفاي»). */
    const val HAS_WIFI = true
}

/** Shared state read from more than one thread (the phone's microphone, sockets). */
typealias SharedVolatile = kotlin.jvm.Volatile

typealias SharedSynchronized = kotlin.jvm.Synchronized

/** Milliseconds since 1970, by the wall clock. */
fun nowMillis(): Long = System.currentTimeMillis()

/** Where blocking work (files, sockets) runs. */
val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher get() = kotlinx.coroutines.Dispatchers.IO

/** Under test: no friends list, invitations or tutorial, nothing reaching the real server. */
fun testMode(): Boolean = System.getProperty("teera.noPresence") == "true"

/** [ms] on this device's calendar: year, day of the year, day of the month, month (1-12). */
fun dateParts(ms: Long): IntArray = java.util.Calendar.getInstance().apply { timeInMillis = ms }.let {
    intArrayOf(
        it.get(java.util.Calendar.YEAR), it.get(java.util.Calendar.DAY_OF_YEAR),
        it.get(java.util.Calendar.DAY_OF_MONTH), it.get(java.util.Calendar.MONTH) + 1,
    )
}

/** A lock the shared code takes around state its callbacks share across threads. */
class Lock

inline fun <T> Lock.locked(block: () -> T): T = synchronized(this, block)

fun uptimeMillis(): Long = System.nanoTime() / 1_000_000

/** Esc does what the phone's back button does: the innermost screen that wants it, first. */
object BackStack {
    class Entry(var enabled: Boolean, var onBack: () -> Unit)

    private val entries = mutableStateListOf<Entry>()

    fun add(entry: Entry) = entries.add(entry)
    fun remove(entry: Entry) = entries.remove(entry)

    /** True when somebody took it. */
    fun back(): Boolean {
        val entry = entries.lastOrNull { it.enabled } ?: return false
        entry.onBack()
        return true
    }
}

@Composable
fun BackPress(enabled: Boolean, onBack: () -> Unit) {
    val latest = rememberUpdatedState(onBack)
    val entry = remember { BackStack.Entry(enabled) { latest.value() } }
    entry.enabled = enabled
    DisposableEffect(entry) {
        BackStack.add(entry)
        onDispose { BackStack.remove(entry) }
    }
}

fun appFont(name: String, weight: FontWeight): Font =
    androidx.compose.ui.text.platform.Font(resource = "font/$name.ttf", weight = weight)

@Composable
fun facePainter(name: String): Painter = painterResource("faces/$name.png")

/** The game's window, for screenshots of it. */
object DesktopWindow {
    @Volatile var current: java.awt.Window? = null
}

@Composable
fun rememberScreenshot(): Picture? = remember {
    runCatching {
        val window = DesktopWindow.current ?: return@runCatching null
        val bounds = java.awt.Rectangle(window.locationOnScreen, window.size)
        java.awt.Robot().createScreenCapture(bounds)
    }.getOrNull()
}

@Composable
fun rememberPicturePicker(onPicked: (Picture) -> Unit): () -> Unit {
    val latest = rememberUpdatedState(onPicked)
    return remember {
        {
            pickImageFile()?.let { file -> runCatching { ImageIO.read(file) }.getOrNull()?.let { latest.value(it) } }
        }
    }
}

/** The system's own file window, for a picture. */
fun pickImageFile(): File? {
    val dialog = java.awt.FileDialog(DesktopWindow.current as? java.awt.Frame, "اختار صورة", java.awt.FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "bmp", "gif") }
    dialog.file = "*.jpg;*.jpeg;*.png;*.bmp;*.gif"
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}

/** The view model's footing on a computer. */
abstract class PlatformViewModel(app: PlatformApp) {
    protected val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    protected val prefs: DesktopPrefs = app.prefs
    protected val filesDir: File = app.dataDir

    /** A file of the app's own, or null if there is none. */
    protected fun readData(name: String): ByteArray? = runCatching { java.io.File(filesDir, name).readBytes() }.getOrNull()

    protected fun writeData(name: String, bytes: ByteArray) {
        java.io.File(filesDir, name).writeBytes(bytes)
    }
    protected val platformApp: PlatformApp = app

    /** No push messages on a computer: the app is open or it is not. */
    @Suppress("UNUSED_PARAMETER")
    protected fun pushToken(onToken: (String) -> Unit) = Unit

    protected open fun onCleared() = Unit

    /** The window is closing. */
    fun close() {
        onCleared()
        scope.cancel()
    }
}

internal object Pictures {
    fun jpegBase64(picture: Picture, maxWidth: Int, maxHeight: Int, quality: Int): String? = runCatching {
        val scale = minOf(1f, maxWidth.toFloat() / picture.width, maxHeight.toFloat() / picture.height)
        val small = scaled(picture, (picture.width * scale).toInt().coerceAtLeast(1), (picture.height * scale).toInt().coerceAtLeast(1))
        java.util.Base64.getEncoder().encodeToString(jpeg(small, quality))
    }.getOrNull()

    fun show(picture: Picture): ImageBitmap = picture.toComposeImageBitmap()

    fun decode(bytes: ByteArray): ImageBitmap? = runCatching {
        org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()

    fun squareJpeg(app: PlatformApp, source: PhotoSource, size: Int, quality: Int): ByteArray? = runCatching {
        val full = ImageIO.read(source) ?: return null
        val side = minOf(full.width, full.height)
        val square = full.getSubimage((full.width - side) / 2, (full.height - side) / 2, side, side)
        jpeg(scaled(square, size, size), quality)
    }.getOrNull()

    /** A copy at [width] x [height], without transparency (JPEG has none). */
    private fun scaled(picture: BufferedImage, width: Int, height: Int): BufferedImage {
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(picture, 0, 0, width, height, null)
        g.dispose()
        return out
    }

    private fun jpeg(picture: BufferedImage, quality: Int): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            val param = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality / 100f
            }
            writer.write(null, IIOImage(picture, null, null), param)
            writer.dispose()
        }
        return out.toByteArray()
    }
}
