package com.teera.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

/*
 * What the phone does its own way. The screens and the view model are shared with the
 * Windows app (teera/desktop), which has a file of the same name answering the same
 * questions for the desktop; everything else is common code.
 */

/** A picture taken or picked on this device (a report's screenshot). */
typealias Picture = android.graphics.Bitmap

/** The app as a whole, which the view model is made with. */
typealias PlatformApp = android.app.Application

/** Where a picked photo comes from. */
typealias PhotoSource = android.net.Uri

object AppInfo {
    val VERSION_CODE: Int get() = BuildConfig.VERSION_CODE
    val VERSION_NAME: String get() = BuildConfig.VERSION_NAME
    val SERVER_URL: String get() = BuildConfig.SERVER_URL
    const val PLATFORM = "android"

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

/** A clock that only goes forward, for the turn rings. */
fun uptimeMillis(): Long = android.os.SystemClock.elapsedRealtime()

@Composable
fun BackPress(enabled: Boolean, onBack: () -> Unit) = androidx.activity.compose.BackHandler(enabled, onBack)

/** A font shipped with the app, by its file name without the extension. */
fun appFont(name: String, weight: FontWeight): Font = Font(
    when (name) {
        "el_messiri_medium" -> R.font.el_messiri_medium
        "el_messiri_bold" -> R.font.el_messiri_bold
        "aref_ruqaa_regular" -> R.font.aref_ruqaa_regular
        else -> R.font.aref_ruqaa_bold
    },
    weight,
)

/** One of the 3D faces (Fluent Emoji, MIT), by its picture's name. */
@Composable
fun facePainter(name: String): Painter = painterResource(
    when (name) {
        "face_joy" -> R.drawable.face_joy
        "face_rofl" -> R.drawable.face_rofl
        "face_smirk" -> R.drawable.face_smirk
        "face_cool" -> R.drawable.face_cool
        "face_think" -> R.drawable.face_think
        "face_eyes" -> R.drawable.face_eyes
        "face_scream" -> R.drawable.face_scream
        "face_cry" -> R.drawable.face_cry
        "face_steam" -> R.drawable.face_steam
        "face_clap" -> R.drawable.face_clap
        "face_muscle" -> R.drawable.face_muscle
        else -> R.drawable.face_fire
    },
)

/** The screen as it is now, taken once when this is first composed; null if it cannot be. */
@Composable
fun rememberScreenshot(): Picture? {
    val view = androidx.compose.ui.platform.LocalView.current
    return androidx.compose.runtime.remember {
        runCatching {
            val root = view.rootView
            android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
                .also { root.draw(android.graphics.Canvas(it)) }
        }.getOrNull()
    }
}

/** Something to call to let the player pick a picture from the phone; [onPicked] gets it. */
@Composable
fun rememberPicturePicker(onPicked: (Picture) -> Unit): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val latest = androidx.compose.runtime.rememberUpdatedState(onPicked)
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
            }.getOrNull()?.let { latest.value(it) }
        }
    }
    return {
        pick.launch(
            androidx.activity.result.PickVisualMediaRequest(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
            ),
        )
    }
}

/** The view model's footing on Android: its scope, its settings, its folder. */
abstract class PlatformViewModel(app: PlatformApp) : AndroidViewModel(app) {
    protected val scope: CoroutineScope get() = viewModelScope
    protected val prefs: android.content.SharedPreferences = app.getSharedPreferences("dominoes", Context.MODE_PRIVATE)
    protected val filesDir: java.io.File = app.filesDir

    /** A file of the app's own, or null if there is none. */
    protected fun readData(name: String): ByteArray? = runCatching { java.io.File(filesDir, name).readBytes() }.getOrNull()

    protected fun writeData(name: String, bytes: ByteArray) {
        java.io.File(filesDir, name).writeBytes(bytes)
    }
    protected val platformApp: PlatformApp get() = getApplication()
    protected fun pushToken(onToken: (String) -> Unit) = Push.token(getApplication(), onToken)
}

/** Pictures in and out of the wire's JPEG. */
internal object Pictures {
    /** [picture] as a JPEG no bigger than [maxWidth] x [maxHeight], in base64. */
    fun jpegBase64(picture: Picture, maxWidth: Int, maxHeight: Int, quality: Int): String? = runCatching {
        val scale = minOf(1f, maxWidth.toFloat() / picture.width, maxHeight.toFloat() / picture.height)
        val small = if (scale < 1f) {
            android.graphics.Bitmap.createScaledBitmap(picture, (picture.width * scale).toInt(), (picture.height * scale).toInt(), true)
        } else picture
        val out = java.io.ByteArrayOutputStream()
        small.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
        java.util.Base64.getEncoder().encodeToString(out.toByteArray())
    }.getOrNull()

    /** [picture] ready to draw. */
    fun show(picture: Picture): ImageBitmap = picture.asImageBitmap()

    fun decode(bytes: ByteArray): ImageBitmap? = runCatching {
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()

    /** A picked photo squared off from the middle and shrunk to [size], as JPEG bytes. */
    fun squareJpeg(app: PlatformApp, source: PhotoSource, size: Int, quality: Int): ByteArray? = runCatching {
        val full = app.contentResolver.openInputStream(source)?.use { android.graphics.BitmapFactory.decodeStream(it) }
            ?: return null
        val side = minOf(full.width, full.height)
        val square = android.graphics.Bitmap.createBitmap(full, (full.width - side) / 2, (full.height - side) / 2, side, side)
        val small = android.graphics.Bitmap.createScaledBitmap(square, size, size, true)
        val out = java.io.ByteArrayOutputStream()
        small.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }.getOrNull()
}
