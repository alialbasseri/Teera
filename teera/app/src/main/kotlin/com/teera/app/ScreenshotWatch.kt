package com.teera.app

import android.app.Activity
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Notices this phone taking a screenshot, so the table can be told. Android only says so
 * from version 14; on older phones nothing is noticed.
 */
class ScreenshotWatch(private val onCaptured: () -> Unit) {
    private var callback: Any? = null

    fun start(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 34) callback = Api34.register(activity, onCaptured)
    }

    fun stop(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 34) callback?.let { Api34.unregister(activity, it) }
        callback = null
    }

    @RequiresApi(34)
    private object Api34 {
        fun register(activity: Activity, onCaptured: () -> Unit): Any {
            val callback = Activity.ScreenCaptureCallback { onCaptured() }
            activity.registerScreenCaptureCallback(activity.mainExecutor, callback)
            return callback
        }

        fun unregister(activity: Activity, callback: Any) {
            activity.unregisterScreenCaptureCallback(callback as Activity.ScreenCaptureCallback)
        }
    }
}
