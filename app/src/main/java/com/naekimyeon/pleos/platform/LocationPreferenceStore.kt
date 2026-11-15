package com.naekimyeon.pleos.platform

import android.content.Context

/** Stores one explicit preference Boolean. Never accepts coordinates, text, fixes, or history. */
internal class LocationPreferenceStore(context: Context, name: String = "foreground_location_preference") {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preferences.getBoolean("use_on_launch", false)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("use_on_launch", enabled).apply()
    }
}
