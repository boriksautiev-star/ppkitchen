package com.ppkitchen.data

import android.content.Context

object BackupPreferences {
    private const val PREFS = "backup_prefs"
    private const val KEY_ENABLED = "auto_backup_enabled"
    private const val KEY_PERIOD_DAYS = "auto_backup_period_days"
    private const val KEY_KEEP_COUNT = "auto_backup_keep_count"
    private const val KEY_FOLDER_URI = "auto_backup_folder_uri"

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()

    fun getPeriodDays(context: Context): Int =
        prefs(context).getInt(KEY_PERIOD_DAYS, 7)

    fun setPeriodDays(context: Context, days: Int) =
        prefs(context).edit().putInt(KEY_PERIOD_DAYS, days).apply()

    fun getKeepCount(context: Context): Int =
        prefs(context).getInt(KEY_KEEP_COUNT, 10)

    fun setKeepCount(context: Context, count: Int) =
        prefs(context).edit().putInt(KEY_KEEP_COUNT, count).apply()

    // URI выбранной пользователем папки (SAF tree URI). null = папка по умолчанию
    fun getFolderUri(context: Context): String? =
        prefs(context).getString(KEY_FOLDER_URI, null)

    fun setFolderUri(context: Context, uri: String?) =
        prefs(context).edit().putString(KEY_FOLDER_URI, uri).apply()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}