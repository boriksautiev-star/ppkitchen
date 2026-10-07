package com.ppkitchen.data

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Двойная страховка для SharedPreferences.
 *
 * При каждом запуске:
 *  - Сохраняет текущие настройки в резервный файл (storage/files/settings_backup.xml)
 *  - Если SharedPreferences пусты, но файл есть — восстанавливает из файла
 *
 * Это спасает, если:
 *  - Android по ошибке очистил SharedPreferences
 *  - Что-то сломалось при обновлении
 *  - Пользователь сбросил кэш приложения
 */
object SettingsBackup {
    private const val TAG = "SettingsBackup"
    private const val BACKUP_FILE = "settings_backup.xml"
    private const val PREFS_NAME = "settings_prefs"

    /** Сохранить текущие настройки в резервный файл */
    fun backup(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.all.isEmpty()) return

            val target = File(context.filesDir, BACKUP_FILE)
            val temp = File(context.filesDir, "$BACKUP_FILE.tmp")

            // Пишем через temp — чтобы при сбое не потерять старую копию
            temp.outputStream().use { output ->
                prefs.all.forEach { (key, value) ->
                    val line = "$key=${value.toString()}\n"
                    output.write(line.toByteArray(Charsets.UTF_8))
                }
            }

            // Заменяем старую копию новой
            if (target.exists()) target.delete()
            temp.renameTo(target)

            Log.d(TAG, "Backup saved: ${prefs.all.size} keys")
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
        }
    }

    /** Восстановить настройки, если SharedPreferences пусты */
    fun restoreIfNeeded(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Если настройки уже есть — не трогаем
            if (prefs.all.isNotEmpty()) return

            val backup = File(context.filesDir, BACKUP_FILE)
            if (!backup.exists()) {
                Log.d(TAG, "No backup file — nothing to restore")
                return
            }

            val lines = backup.readLines(Charsets.UTF_8)
            val editor = prefs.edit()
            var restored = 0

            lines.forEach { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) return@forEach
                val key = line.substring(0, idx)
                val valueStr = line.substring(idx + 1)

                // Тип определяем по значению
                val restoredValue: Any? = when {
                    valueStr == "true" || valueStr == "false" -> valueStr.toBoolean()
                    valueStr.toIntOrNull() != null -> valueStr.toInt()
                    valueStr.toLongOrNull() != null -> valueStr.toLong()
                    valueStr.toFloatOrNull() != null -> valueStr.toFloat()
                    else -> valueStr
                }

                when (restoredValue) {
                    is Boolean -> editor.putBoolean(key, restoredValue)
                    is Int -> editor.putInt(key, restoredValue)
                    is Long -> editor.putLong(key, restoredValue)
                    is Float -> editor.putFloat(key, restoredValue)
                    is String -> editor.putString(key, restoredValue)
                }
                restored++
            }

            editor.apply()
            Log.d(TAG, "Restored $restored keys from backup")
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
        }
    }
}