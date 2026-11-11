package com.teera.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.await
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int16Array
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import org.w3c.fetch.Response

/*
 * What a browser does its own way: the web's answers to the questions the phone's
 * Platform.kt answers for Android. Everything else is the phone's code, shared.
 */

typealias Picture = ImageBitmap

/** The game in this browser tab: its settings live in the browser's own storage. */
class WebApp {
    val prefs = WebPrefs()
}

typealias PlatformApp = WebApp

/** A picked photo: its encoded bytes. */
typealias PhotoSource = ByteArray

private fun pageHost(): String = js("location.host")
private fun pageSecure(): Boolean = js("location.protocol === 'https:'")

object AppInfo {
    /** The web build number the server books (as 30000 + this). Up by one each release. */
    const val VERSION_CODE = 1
    const val VERSION_NAME = "1.0.0"

    /**
     * The game's own server when the page comes from it (/play); the live one when it is
     * opened from anywhere else (a local test).
     */
    val SERVER_URL: String by lazy {
        val host = pageHost()
        val local = host.isEmpty() || host.startsWith("localhost") || host.startsWith("127.")
        if (local) "wss://teera-xfvr.onrender.com/ws" else (if (pageSecure()) "wss://" else "ws://") + host + "/ws"
    }
    const val PLATFORM = "web"

    /** Whether this app can play a table on the Wi-Fi (the menu's «وايفاي»). */
    const val HAS_WIFI = false
}

private fun jsNow(): Double = js("Date.now()")
private fun jsUptime(): Double = js("performance.now()")

/** year,dayOfYear,day,month for [ms] in the browser's own time zone. */
private fun jsDateParts(ms: Double): String = js(
    "(() => { const d = new Date(ms); const start = new Date(d.getFullYear(), 0, 0); " +
        "return d.getFullYear() + ',' + Math.floor((d - start) / 86400000) + ',' + d.getDate() + ',' + (d.getMonth() + 1); })()",
)

/** One thread in a browser: these mark what other platforms share between threads. */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class SharedVolatile

@Target(AnnotationTarget.FUNCTION)
annotation class SharedSynchronized

fun nowMillis(): Long = jsNow().toLong()

fun uptimeMillis(): Long = jsUptime().toLong()

/** A browser runs everything on one thread; there is no separate pool for blocking work. */
val ioDispatcher: CoroutineDispatcher get() = Dispatchers.Default

fun testMode(): Boolean = false

fun dateParts(ms: Long): IntArray = jsDateParts(ms.toDouble()).split(',').map { it.toInt() }.toIntArray()

/** One thread: nothing to lock against. */
class Lock

inline fun <T> Lock.locked(block: () -> T): T = block()

/** Esc does what the phone's back button does: the innermost screen that wants it, first. */
object BackStack {
    class Entry(var enabled: Boolean, var onBack: () -> Unit)

    private val entries = mutableStateListOf<Entry>()

    fun add(entry: Entry) = entries.add(entry)
    fun remove(entry: Entry) = entries.remove(entry)

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

// -- files the page fetches before the game starts -----------------------------------

fun Int8Array.toByteArray(): ByteArray = ByteArray(length) { this[it] }

fun ByteArray.toInt8Array(): Int8Array {
    val array = Int8Array(size)
    for (i in indices) array[i] = this[i]
    return array
}

fun Int16Array.toShortArray(): ShortArray = ShortArray(length) { this[it] }

fun ShortArray.toInt16Array(): Int16Array {
    val array = Int16Array(size)
    for (i in indices) array[i] = this[i]
    return array
}

/** A file next to the page, or null. */
suspend fun fetchBytes(path: String): ByteArray? = runCatching {
    val response = window.fetch(path).await<Response>()
    if (!response.ok) return null
    Int8Array(response.arrayBuffer().await<ArrayBuffer>()).toByteArray()
}.getOrNull()

/**
 * Skia in the browser has no fonts of its own, so the game's are fetched before the first
 * screen is drawn, and the faces with them.
 */
object WebAssets {
    val fonts = HashMap<String, ByteArray>()
    val faces = HashMap<String, ImageBitmap>()

    // Noto Emoji (SIL OFL) stands in for the emoji in the game's words: Skia here has none.
    private val fontNames = listOf(
        "el_messiri_medium", "el_messiri_bold", "aref_ruqaa_regular", "aref_ruqaa_bold",
        "noto_emoji", "symbols_a", "symbols_b", "symbols_c",
    )

    /** Fonts that only stand in for characters the game's own fonts lack. */
    val fallbacks = listOf("noto_emoji", "symbols_a", "symbols_b", "symbols_c")
    private val faceNames = listOf(
        "face_joy", "face_rofl", "face_smirk", "face_cool", "face_think", "face_eyes",
        "face_scream", "face_cry", "face_steam", "face_clap", "face_muscle", "face_fire",
    )

    suspend fun load() {
        fontNames.forEach { name -> fetchBytes("fonts/$name.ttf")?.let { fonts[name] = it } }
        faceNames.forEach { name ->
            fetchBytes("faces/$name.png")?.let { bytes -> Pictures.decode(bytes)?.let { faces[name] = it } }
        }
    }
}

fun appFont(name: String, weight: FontWeight): Font =
    androidx.compose.ui.text.platform.Font(identity = name, data = WebAssets.fonts[name] ?: ByteArray(0), weight = weight)

@Composable
fun facePainter(name: String): Painter = remember(name) {
    WebAssets.faces[name]?.let { BitmapPainter(it) } ?: ColorPainter(Color.Transparent)
}

/** A browser cannot photograph its own page; a report goes without the screen. */
@Composable
fun rememberScreenshot(): Picture? = null

private fun jsPickImage(onPicked: (Int8Array) -> Unit): Unit = js(
    "{ const input = document.createElement('input'); input.type = 'file'; input.accept = 'image/*';" +
        " input.onchange = () => { const f = input.files && input.files[0]; if (f) f.arrayBuffer().then(b => onPicked(new Int8Array(b))); };" +
        " input.click(); }",
)

/** The computer's or phone's own file picker, for a picture; [onPicked] gets its bytes. */
fun pickImage(onPicked: (ByteArray) -> Unit) = jsPickImage { array -> onPicked(array.toByteArray()) }

@Composable
fun rememberPicturePicker(onPicked: (Picture) -> Unit): () -> Unit {
    val latest = rememberUpdatedState(onPicked)
    return remember {
        { pickImage { bytes -> Pictures.decode(bytes)?.let { latest.value(it) } } }
    }
}

/** The view model's footing in a browser: storage is the browser's own. */
abstract class PlatformViewModel(app: PlatformApp) {
    protected val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    protected val prefs: WebPrefs = app.prefs
    protected val platformApp: PlatformApp = app

    protected fun readData(name: String): ByteArray? =
        localStorage.getItem("teera.file.$name")?.let { unbase64(it) }

    protected fun writeData(name: String, bytes: ByteArray) {
        runCatching { localStorage.setItem("teera.file.$name", base64(bytes)) }
    }

    /** No push messages in a browser: the page is open or it is not. */
    @Suppress("UNUSED_PARAMETER")
    protected fun pushToken(onToken: (String) -> Unit) = Unit

    protected open fun onCleared() = Unit
}

internal object Pictures {
    fun jpegBase64(picture: Picture, maxWidth: Int, maxHeight: Int, quality: Int): String? = runCatching {
        val scale = minOf(1f, maxWidth.toFloat() / picture.width, maxHeight.toFloat() / picture.height)
        val image = Image.makeFromBitmap(picture.asSkiaBitmap())
        val bytes = encode(image, (picture.width * scale).toInt().coerceAtLeast(1), (picture.height * scale).toInt().coerceAtLeast(1), quality)
            ?: return null
        base64(bytes)
    }.getOrNull()

    fun show(picture: Picture): ImageBitmap = picture

    fun decode(bytes: ByteArray): ImageBitmap? = runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

    fun squareJpeg(app: PlatformApp, source: PhotoSource, size: Int, quality: Int): ByteArray? = runCatching {
        val full = Image.makeFromEncoded(source)
        val side = minOf(full.width, full.height)
        val surface = Surface.makeRasterN32Premul(size, size)
        surface.canvas.drawImageRect(
            full,
            Rect.makeXYWH((full.width - side) / 2f, (full.height - side) / 2f, side.toFloat(), side.toFloat()),
            Rect.makeWH(size.toFloat(), size.toFloat()),
        )
        surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, quality)?.bytes
    }.getOrNull()

    private fun encode(image: Image, width: Int, height: Int, quality: Int): ByteArray? {
        val surface = Surface.makeRasterN32Premul(width, height)
        surface.canvas.drawImageRect(image, Rect.makeWH(width.toFloat(), height.toFloat()))
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, quality)?.bytes
    }
}
