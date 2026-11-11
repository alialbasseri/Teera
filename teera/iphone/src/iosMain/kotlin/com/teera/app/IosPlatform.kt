package com.teera.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import platform.Foundation.NSBundle
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile

/*
 * What an iPhone does its own way: the iOS answers to the questions the phone's Platform.kt
 * answers for Android. Everything else is the phone's code, shared.
 */

/** A picture taken or picked on this device. */
typealias Picture = ImageBitmap

/** The app as a whole: where it keeps its files and settings. */
class IosApp {
    val prefs = IosPrefs()

    /** The app's own documents folder. */
    val dataDir: String = (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String)
        ?: NSProcessInfo.processInfo.environment["HOME"] as? String ?: "."
}

typealias PlatformApp = IosApp

/** A picked photo: its encoded bytes (JPEG or PNG). */
typealias PhotoSource = ByteArray

object AppInfo {
    /** The iPhone build number the server books (as 10000 + this). Up by one each release. */
    const val VERSION_CODE = 1
    const val VERSION_NAME = "1.0.0"
    const val SERVER_URL = "wss://teera-xfvr.onrender.com/ws"
    const val PLATFORM = "ios"

    /** Whether this app can play a table on the Wi-Fi (the menu's «وايفاي»). */
    const val HAS_WIFI = true
}

/** Shared state read from more than one thread. */
typealias SharedVolatile = kotlin.concurrent.Volatile

/** The iPhone's voice touches its packets under its own lock (IosVoice.kt). */
@Target(AnnotationTarget.FUNCTION)
annotation class SharedSynchronized

fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

fun uptimeMillis(): Long = (NSProcessInfo.processInfo.systemUptime * 1000).toLong()

val ioDispatcher: CoroutineDispatcher get() = Dispatchers.IO

fun testMode(): Boolean = false

fun dateParts(ms: Long): IntArray {
    val date = NSDate.dateWithTimeIntervalSince1970(ms / 1000.0)
    val calendar = NSCalendar.currentCalendar
    val parts = calendar.components(NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay, fromDate = date)
    val dayOfYear = calendar.ordinalityOfUnit(NSCalendarUnitDay, inUnit = NSCalendarUnitYear, forDate = date)
    return intArrayOf(parts.year.toInt(), dayOfYear.toInt(), parts.day.toInt(), parts.month.toInt())
}

/** A lock the shared code takes around state its callbacks share across threads. */
class Lock {
    val lock = NSRecursiveLock()
}

inline fun <T> Lock.locked(block: () -> T): T {
    lock.lock()
    try {
        return block()
    } finally {
        lock.unlock()
    }
}

/** An iPhone has no back button: every screen has its own way out. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun BackPress(enabled: Boolean, onBack: () -> Unit) = Unit

/**
 * A file shipped in the app, under GameFiles/ in the Xcode project. (Not "Resources": iOS
 * takes a folder of that name at the top of an app for a Mac-style bundle and cannot read it.)
 */
fun bundled(folder: String, name: String, type: String): ByteArray? {
    val path = NSBundle.mainBundle.pathForResource(name, ofType = type, inDirectory = "GameFiles/$folder")
        ?: return null
    return NSData.dataWithContentsOfFile(path)?.toByteArray()
}

fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return bytes!!.readBytes(size)
}

fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
}

fun appFont(name: String, weight: FontWeight): Font =
    androidx.compose.ui.text.platform.Font(
        identity = name,
        data = bundled("Fonts", name, "ttf") ?: ByteArray(0),
        weight = weight,
    )

private val faces = HashMap<String, ImageBitmap?>()

@Composable
fun facePainter(name: String): Painter = remember(name) {
    val picture = faces.getOrPut(name) {
        bundled("Emotes", name, "png")?.let { runCatching { Image.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull() }
    }
    if (picture != null) BitmapPainter(picture) else ColorPainter(androidx.compose.ui.graphics.Color.Transparent)
}

/** Not on the iPhone yet: a report goes without the screen. */
@Composable
fun rememberScreenshot(): Picture? = null

@Composable
fun rememberPicturePicker(onPicked: (Picture) -> Unit): () -> Unit {
    val latest = rememberUpdatedState(onPicked)
    return remember {
        {
            PhotoPicker.pick { bytes ->
                runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()?.let { latest.value(it) }
            }
        }
    }
}

/** The view model's footing on an iPhone. */
abstract class PlatformViewModel(app: PlatformApp) {
    protected val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    protected val prefs: IosPrefs = app.prefs
    protected val platformApp: PlatformApp = app
    private val dataDir = app.dataDir

    protected fun readData(name: String): ByteArray? {
        val path = "$dataDir/$name"
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
        return NSData.dataWithContentsOfFile(path)?.toByteArray()
    }

    protected fun writeData(name: String, bytes: ByteArray) {
        bytes.toNSData().writeToFile("$dataDir/$name", atomically = true)
    }

    /** No push messages without an Apple developer account. */
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
        val left = (full.width - side) / 2f
        val top = (full.height - side) / 2f
        surface.canvas.drawImageRect(
            full,
            Rect.makeXYWH(left, top, side.toFloat(), side.toFloat()),
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
