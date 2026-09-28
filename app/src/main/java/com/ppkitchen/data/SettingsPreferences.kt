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
    private const val KEY_THEME_MODE = "theme_mode"

    // Режим графика выручки
    private const val KEY_CHART_MODE = "chart_mode"

    // Уведомление об окончании питания клиента
    private const val KEY_CLIENT_END_ENABLED = "client_end_reminder_enabled"
    private const val KEY_CLIENT_END_HOUR = "client_end_reminder_hour"
    private const val KEY_CLIENT_END_MINUTE = "client_end_reminder_minute"
    private const val KEY_CLIENT_END_TEXT = "client_end_reminder_text"
    private const val KEY_CLIENT_END_NEXT_SCHEDULE = "client_end_reminder_next_schedule"
    private const val KEY_CLIENT_END_MODE = "client_end_reminder_mode"

    // Текст сообщения для продления
    private const val KEY_RENEWAL_MESSAGE_TEXT = "renewal_message_text"

    // Автоматические расходы на поваров
    private const val KEY_COOK_REMINDER_ENABLED = "cook_reminder_enabled"
    private const val KEY_COOK_REMINDER_HOUR = "cook_reminder_hour"
    private const val KEY_COOK_REMINDER_MINUTE = "cook_reminder_minute"
    private const val KEY_COOK_REMINDER_NEXT_SCHEDULE = "cook_reminder_next_schedule"

    private const val DEFAULT_CLIENT_END_TEXT =
        "{имя}\n{дата}, {калории} ккал\n{адрес}\n{телефон}"

    private const val DEFAULT_RENEWAL_TEXT =
        "Здравствуйте, {имя}!\n\nЗавтра ({дата}) заканчивается ваш курс питания ({калории} ккал).\nХотите продлить? Напишите, пожалуйста, ответ.\n\nС уважением,\nPP Kitchen"

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
    fun getThemeMode(context: Context): String =
        prefs(context).getString(KEY_THEME_MODE, "system") ?: "system"

    fun setThemeMode(context: Context, mode: String) =
        prefs(context).edit().putString(KEY_THEME_MODE, mode).apply()

    // === Режим графика выручки ===
    fun getChartMode(context: Context): String =
        prefs(context).getString(KEY_CHART_MODE, "income") ?: "income"

    fun setChartMode(context: Context, mode: String) =
        prefs(context).edit().putString(KEY_CHART_MODE, mode).apply()

    // === Уведомление об окончании питания клиента ===
    fun isClientEndReminderEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLIENT_END_ENABLED, false)

    fun setClientEndReminderEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_CLIENT_END_ENABLED, enabled).apply()

    fun getClientEndReminderHour(context: Context): Int =
        prefs(context).getInt(KEY_CLIENT_END_HOUR, 9)

    fun setClientEndReminderHour(context: Context, hour: Int) =
        prefs(context).edit().putInt(KEY_CLIENT_END_HOUR, hour.coerceIn(0, 23)).apply()

    fun getClientEndReminderMinute(context: Context): Int =
        prefs(context).getInt(KEY_CLIENT_END_MINUTE, 0)

    fun setClientEndReminderMinute(context: Context, minute: Int) =
        prefs(context).edit().putInt(KEY_CLIENT_END_MINUTE, minute.coerceIn(0, 59)).apply()

    fun getClientEndReminderText(context: Context): String =
        prefs(context).getString(KEY_CLIENT_END_TEXT, DEFAULT_CLIENT_END_TEXT)
            ?: DEFAULT_CLIENT_END_TEXT

    fun setClientEndReminderText(context: Context, text: String) =
        prefs(context).edit().putString(KEY_CLIENT_END_TEXT, text).apply()

    fun getClientEndReminderNextSchedule(context: Context): Long =
        prefs(context).getLong(KEY_CLIENT_END_NEXT_SCHEDULE, 0L)

    fun setClientEndReminderNextSchedule(context: Context, timestamp: Long) =
        prefs(context).edit().putLong(KEY_CLIENT_END_NEXT_SCHEDULE, timestamp).apply()

    fun getClientEndReminderMode(context: Context): String =
        prefs(context).getString(KEY_CLIENT_END_MODE, "single") ?: "single"

    fun setClientEndReminderMode(context: Context, mode: String) =
        prefs(context).edit().putString(KEY_CLIENT_END_MODE, mode).apply()

    // === Текст сообщения для продления ===
    fun getRenewalMessageText(context: Context): String =
        prefs(context).getString(KEY_RENEWAL_MESSAGE_TEXT, DEFAULT_RENEWAL_TEXT)
            ?: DEFAULT_RENEWAL_TEXT

    fun setRenewalMessageText(context: Context, text: String) =
        prefs(context).edit().putString(KEY_RENEWAL_MESSAGE_TEXT, text).apply()

    // === Автоматические расходы на поваров ===
    fun isCookReminderEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COOK_REMINDER_ENABLED, false)

    fun setCookReminderEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_COOK_REMINDER_ENABLED, enabled).apply()

    fun getCookReminderHour(context: Context): Int =
        prefs(context).getInt(KEY_COOK_REMINDER_HOUR, 22)

    fun setCookReminderHour(context: Context, hour: Int) =
        prefs(context).edit().putInt(KEY_COOK_REMINDER_HOUR, hour.coerceIn(0, 23)).apply()

    fun getCookReminderMinute(context: Context): Int =
        prefs(context).getInt(KEY_COOK_REMINDER_MINUTE, 0)

    fun setCookReminderMinute(context: Context, minute: Int) =
        prefs(context).edit().putInt(KEY_COOK_REMINDER_MINUTE, minute.coerceIn(0, 59)).apply()

    fun getCookReminderNextSchedule(context: Context): Long =
        prefs(context).getLong(KEY_COOK_REMINDER_NEXT_SCHEDULE, 0L)

    fun setCookReminderNextSchedule(context: Context, timestamp: Long) =
        prefs(context).edit().putLong(KEY_COOK_REMINDER_NEXT_SCHEDULE, timestamp).apply()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}