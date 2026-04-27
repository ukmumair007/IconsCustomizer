package com.ukm.app.iconscustomizer

import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.core.content.edit
import com.ukm.app.iconscustomizer.MainActivity.Companion.PREF_NAME

object UIHelpers {

    fun restartLauncher(context: Context): Boolean {
        return try {
            val intent = android.content.Intent("com.ukm.app.RELOAD_ICONS")
            intent.setPackage("com.miui.home")
            context.sendBroadcast(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun pushRemotePref(changedKey: String, newValue: Any): Boolean {
        val service = App.mService ?: return false
        val remotePrefs = service.getRemotePreferences(PREF_NAME)

        remotePrefs.edit {
            when (newValue) {
                is String -> putString(changedKey, newValue.trim())
                is Boolean -> putBoolean(changedKey, newValue)
                is Int -> putInt(changedKey, newValue)
            }
        }
        return true
    }

    fun pushLocalPref(context: Context, changedKey: String, newValue: Any) {
        val localPrefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        localPrefs.edit(commit = true) {
            when (newValue) {
                is String -> putString(changedKey, newValue.trim())
                is Boolean -> putBoolean(changedKey, newValue)
                is Int -> putInt(changedKey, newValue)
            }
        }
    }
}