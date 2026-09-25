package com.ppkitchen.data

import android.content.Context

object SettingsPreferences {
    private const val PREFS = "settings_prefs"
    private const val KEY_COURIER_RATE = "courier_rate_per_point"
    private const val KEY_AUTO_DELIVERY = "auto_delivery"

    // Автоотправка повару
    private const val KEY_CHEF_ENABLED = "chef_reminder_enabled"
    private const val KEY_CHEF_HOUR = "chef_send_hour"
    private const val KEY_CHEF_MINUTE = "chef_send_minute"
    private const val KEY_CHEF_PHONE = "chef_phone"
    private const val KEY_CHEF_MESSENGER = "chef_messenger"
    private const val KEY_CHEF_NEXT_SCHEDULE = "chef_next_schedule"

    // Тема
    private const val KEY_THEME_MODE = "theme_mode"  // "system" | "light" | "dark"

    // Режим графика выручки
    // "income" — поступления (кассовый метод)
    // "accrual" — по дням питания (метод начисления)
    // "both" — оба графика
    private const val KEY_CHART_MODE = "chart_mode"

    fun getCourierRate(context: Context): Int =
        prefs(context).getInt(KEY_COURIER_RATE, 200)

    fun setCourierRate(context: Context, rate: Int) =
        prefs(context).edit().putInt(KEY_COURIER_RATE, rate).apply()

    fun isAutoDelivery(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_DELIVERY, true)

    fun setAutoDelivery(context: Context, auto: Boolean) =
        prefs(context).edit().putBoolean(KEY_AUTO_DELIVERY, auto).apply()

    // === Автоотправка повару ===
    fun isChefReminderEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CHEF_ENABLED, false)

    fun setChefReminderEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_CHEF_ENABLED, enabled).apply()

    fun getChefSendHour(context: Context): Int =
        prefs(context).getInt(KEY_CHEF_HOUR, 7)

    fun setChefSendHour(context: Context, hour: Int) =
        prefs(context).edit().putInt(KEY_CHEF_HOUR, hour.coerceIn(0, 23)).apply()

    fun getChefSendMinute(context: Context): Int =
        prefs(context).getInt(KEY_CHEF_MINUTE, 0)

    fun setChefSendMinute(context: Context, minute: Int) =
        prefs(context).edit().putInt(KEY_CHEF_MINUTE, minute.coerceIn(0, 59)).apply()

    fun getChefPhone(context: Context): String =
        prefs(context).getString(KEY_CHEF_PHONE, "") ?: ""

    fun setChefPhone(context: Context, phone: String) =
        prefs(context).edit().putString(KEY_CHEF_PHONE, phone.trim()).apply()

    fun getChefMessenger(context: Context): String =
        prefs(context).getString(KEY_CHEF_MESSENGER, "whatsapp") ?: "whatsapp"

    fun setChefMessenger(context: Context, messenger: String) =
        prefs(context).edit().putString(KEY_CHEF_MESSENGER, messenger).apply()

    fun getChefNextSchedule(context: Context): Long =
        prefs(context).getLong(KEY_CHEF_NEXT_SCHEDULE, 0L)

    fun setChefNextSchedule(context: Context, timestamp: Long) =
        prefs(context).edit().putLong(KEY_CHEF_NEXT_SCHEDULE, timestamp).apply()

    // === Тема ===
    /** "system" | "light" | "dark" */
    fun getThemeMode(context: Context): String =
        prefs(context).getString(KEY_THEME_MODE, "system") ?: "system"

    fun setThemeMode(context: Context, mode: String) =
        prefs(context).edit().putString(KEY_THEME_MODE, mode).apply()

    // === Режим графика выручки ===
    /** "income" | "accrual" | "both" */
    fun getChartMode(context: Context): String =
        prefs(context).getString(KEY_CHART_MODE, "income") ?: "income"

    fun setChartMode(context: Context, mode: String) =
        prefs(context).edit().putString(KEY_CHART_MODE, mode).apply()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}