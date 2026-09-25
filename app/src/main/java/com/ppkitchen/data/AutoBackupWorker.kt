package com.ppkitchen.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "AutoBackupWorker"
private const val DB_NAME = "ppkitchen.db"

class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val ctx = applicationContext

            if (!BackupPreferences.isEnabled(ctx)) {
                Log.d(TAG, "Disabled, skipping")
                return Result.success()
            }

            val db = AppDatabase.get(ctx)
            try {
                db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
            } catch (e: Exception) {
                Log.w(TAG, "Checkpoint failed", e)
            }

            val dbPath = ctx.getDatabasePath(DB_NAME)
            if (!dbPath.exists()) {
                Log.e(TAG, "DB file not found")
                return Result.retry()
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
            val fileName = "ppkitchen_backup_$timestamp.db"

            val folderUri = BackupPreferences.getFolderUri(ctx)
            if (folderUri != null) {
                // Пишем в выбранную пользователем папку
                val ok = writeToSafFolder(ctx, Uri.parse(folderUri), fileName, dbPath)
                if (!ok) {
                    Log.e(TAG, "SAF write failed, falling back to default")
                    writeToDefaultFolder(ctx, fileName, dbPath)
                }
            } else {
                // Папка по умолчанию
                writeToDefaultFolder(ctx, fileName, dbPath)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            Result.retry()
        }
    }

    private fun writeToSafFolder(
        ctx: Context,
        treeUri: Uri,
        fileName: String,
        dbPath: File
    ): Boolean {
        return try {
            val dir = DocumentFile.fromTreeUri(ctx, treeUri)
            if (dir == null || !dir.canWrite()) {
                Log.e(TAG, "Cannot write to folder")
                return false
            }

            // Удаляем существующий файл с таким именем, если есть
            dir.findFile(fileName)?.delete()

            val newFile = dir.createFile("application/octet-stream", fileName) ?: return false
            ctx.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                dbPath.inputStream().use { input -> input.copyTo(output) }
            }

            Log.d(TAG, "SAF backup written: ${newFile.uri}")

            // Ротация
            rotateSafBackups(dir, BackupPreferences.getKeepCount(ctx))

            true
        } catch (e: Exception) {
            Log.e(TAG, "SAF write failed", e)
            false
        }
    }

    private fun writeToDefaultFolder(ctx: Context, fileName: String, dbPath: File) {
        try {
            val docsDir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: ctx.filesDir
            val backupDir = File(docsDir, "backups").apply { mkdirs() }
            val backupFile = File(backupDir, fileName)

            dbPath.inputStream().use { input ->
                backupFile.outputStream().use { output -> input.copyTo(output) }
            }

            Log.d(TAG, "Default backup written: ${backupFile.absolutePath}")
            rotateDefaultBackups(backupDir, BackupPreferences.getKeepCount(ctx))
        } catch (e: Exception) {
            Log.e(TAG, "Default write failed", e)
        }
    }

    private fun rotateDefaultBackups(dir: File, keep: Int) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() } ?: return
        files.drop(keep).forEach { it.delete() }
    }

    private fun rotateSafBackups(dir: DocumentFile, keep: Int) {
        val files = dir.listFiles()
            .filter { it.isFile && it.name?.endsWith(".db") == true }
            .sortedByDescending { it.lastModified() }
        files.drop(keep).forEach { it.delete() }
    }
}