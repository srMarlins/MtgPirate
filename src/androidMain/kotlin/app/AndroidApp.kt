package app

import android.content.Context

/**
 * Static application context holder for Android.
 * Set in MainActivity.onCreate() before any Compose content.
 */
object AndroidApp {
    var appContext: Context? = null
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
