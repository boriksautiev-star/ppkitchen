package com.ppkitchen.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ppkitchen.data.AppDatabase
import com.ppkitchen.data.AutoBackupWorker
import com.ppkitchen.data.BackupPreferences
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private const val TAG = "BackupManager"
private const val DB_NAME = "ppkitchen.db"
private const val WORK_NAME = "auto_backup_work"

// ---------- Ручное сохранение (поделиться) ----------
fun backupDatabaseAndShare(context: Context) {
    try {
        val db = AppDatabase.get(context)
        try {
            db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
        } catch (e: Exception) {
            Log.w(TAG, "Checkpoint failed", e)
        }

        val dbPath = context.getDatabasePath(DB_NAME)
        if (!dbPath.exists()) return

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
        val backupFile = File(dir, "ppkitchen_backup_$timestamp.db")

        dbPath.inputStream().use { input ->
            backupFile.outputStream().use { output -> input.copyTo(output) }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            backupFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PP Kitchen — резервная копия $timestamp")
            putExtra(Intent.EXTRA_TEXT, "Резервная копия PP Kitchen от $timestamp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Отправить резервную копию"))
    } catch (e: Exception) {
        Log.e(TAG, "Backup failed", e)
        throw e
    }
}

// ---------- Восстановление ----------
fun restoreDatabase(context: Context, uri: Uri): Boolean {
    return try {
        AppDatabase.reset()
        val dbPath = context.getDatabasePath(DB_NAME)
        dbPath.parentFile?.mkdirs()

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dbPath).use { output -> input.copyTo(output) }
        } ?: return false

        File("${dbPath.path}-wal").delete()
        File("${dbPath.path}-shm").delete()
        true
    } catch (e: Exception) {
        Log.e(TAG, "Restore failed", e)
        false
    }
}

// ---------- Планирование ----------
fun scheduleAutoBackup(context: Context) {
    if (!BackupPreferences.isEnabled(context)) {
        cancelAutoBackup(context)
        return
    }
    val days = BackupPreferences.getPeriodDays(context).coerceAtLeast(1)

    val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
        days.toLong(), TimeUnit.DAYS
    ).setConstraints(Constraints.NONE).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WORK_NAME,
        ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
    Log.d(TAG, "Scheduled every $days days")
}

fun cancelAutoBackup(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
}

fun runBackupNow(context: Context) {
    val request = OneTimeWorkRequestBuilder<AutoBackupWorker>().build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        "manual_backup_now",
        ExistingWorkPolicy.REPLACE,
        request
    )
}

// ---------- Отправка последней копии ----------
fun getLastBackupFile(context: Context): File? {
    // 1. Из выбранной папки (если есть)
    val folderUri = BackupPreferences.getFolderUri(context)
    if (folderUri != null) {
        try {
            val dir = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
            val files = dir?.listFiles()?.filter {
                it.isFile && it.name?.endsWith(".db") == true
            }?.sortedByDescending { it.lastModified() }
            val doc = files?.firstOrNull()
            if (doc != null) {
                // Копируем во внутреннюю папку, чтобы отдать через FileProvider
                val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val local = File(cacheDir, doc.name ?: "backup.db")
                context.contentResolver.openInputStream(doc.uri)?.use { input ->
                    local.outputStream().use { output -> input.copyTo(output) }
                }
                return local
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read from SAF folder", e)
        }
    }

    // 2. Из папки по умолчанию
    val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return null
    val backupDir = File(docsDir, "backups")
    if (!backupDir.exists()) return null
    return backupDir.listFiles { f -> f.isFile && f.name.endsWith(".db") }
        ?.maxByOrNull { it.lastModified() }
}

fun shareLastBackup(context: Context): Boolean {
    val file = getLastBackupFile(context) ?: return false
    return try {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val copy = File(dir, file.name)
        // Если это уже cache-файл — не копируем
        if (file.absolutePath != copy.absolutePath) {
            file.inputStream().use { input ->
                copy.outputStream().use { output -> input.copyTo(output) }
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            copy
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PP Kitchen — резервная копия")
            putExtra(Intent.EXTRA_TEXT, "Резервная копия PP Kitchen: ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Отправить резервную копию"))
        true
    } catch (e: Exception) {
        Log.e(TAG, "Share last backup failed", e)
        false
    }
}