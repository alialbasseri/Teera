package com.teera.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Notifications that reach the phone with the app closed: an invitation to a table, or
 * the table waiting on this player. Firebase is set up here from the project's values
 * (Ali's "teera" project) rather than through the google-services plugin.
 */
object Push {
    const val CHANNEL = "teera"
    private const val KEY_TOKEN = "pushToken"

    /** Where a notification wants to take the player: a room's code, in the tap's extras. */
    const val EXTRA_CODE = "code"

    private val options = FirebaseOptions.Builder()
        .setApplicationId("1:825571352727:android:068965d404da5ee29e49d2")
        .setApiKey("AIzaSyDyhuwKIjZpEKfTvIA0BHvIjE2zRIGBnng")
        .setProjectId("teera-eb215")
        .setGcmSenderId("825571352727")
        .build()

    /** Only the build Firebase knows (the debug package); tests and other builds go without. */
    private fun usable(): Boolean =
        BuildConfig.APPLICATION_ID == "com.teera.app.debug" && System.getProperty("teera.noPresence") != "true"

    fun start(context: Context) {
        if (!usable()) return
        runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context, options)
        }
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL, "العزايم ودورك", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "من جماعتك يعزمونك، أو الطاولة تنتظرك"
                },
            )
        }
    }

    /** This phone's address for notifications, once Firebase has one; [onToken] may run later. */
    fun token(context: Context, onToken: (String) -> Unit) {
        if (!usable()) return
        prefs(context).getString(KEY_TOKEN, null)?.let(onToken)
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (token.isNullOrBlank()) return@addOnSuccessListener
                prefs(context).edit().putString(KEY_TOKEN, token).apply()
                onToken(token)
            }
        }
    }

    internal fun remember(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("dominoes", Context.MODE_PRIVATE)
}

/**
 * Firebase's side of it. With the app closed the phone shows the notification itself;
 * open, the app already hears invitations over its own connection, so nothing more is
 * shown here.
 */
class TeeraMessaging : FirebaseMessagingService() {
    override fun onNewToken(token: String) = Push.remember(applicationContext, token)

    override fun onMessageReceived(message: RemoteMessage) = Unit
}
