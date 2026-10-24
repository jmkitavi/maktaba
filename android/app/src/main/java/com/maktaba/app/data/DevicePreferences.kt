package com.maktaba.app.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

enum class AppAppearance(val label: String) {
    SYSTEM("System · follow device"), LIGHT("Light"), DARK("Dark")
}

object DevicePreferences {
    private const val FILE = "book_haven_device"
    private const val APPEARANCE = "appearance"
    private const val NOTIFICATIONS_REQUESTED = "notifications_requested"

    var appearance by mutableStateOf(AppAppearance.SYSTEM)
        private set
    var notificationsRequested by mutableStateOf(false)
        private set

    fun initialize(context: Context) {
        val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val stored = preferences.getString(APPEARANCE, null)
        appearance = AppAppearance.values().find { it.name == stored } ?: AppAppearance.SYSTEM.also {
            if (stored != null) Log.w("DevicePreferences", "Unknown saved appearance; using System.")
        }
        notificationsRequested = preferences.getBoolean(NOTIFICATIONS_REQUESTED, false)
    }

    suspend fun setAppearance(context: Context, value: AppAppearance) {
        val saved = withContext(Dispatchers.IO) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putString(APPEARANCE, value.name).commit()
        }
        if (!saved) throw IOException("Appearance could not be saved. Please try again.")
        appearance = value
    }

    suspend fun markNotificationsRequested(context: Context) {
        val saved = withContext(Dispatchers.IO) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putBoolean(NOTIFICATIONS_REQUESTED, true).commit()
        }
        if (!saved) throw IOException("Notification preferences could not be saved. Please try again.")
        notificationsRequested = true
    }
}
