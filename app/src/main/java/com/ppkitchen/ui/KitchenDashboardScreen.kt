package com.ppkitchen.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.ppkitchen.data.AppDatabase
import com.ppkitchen.data.ExcelHelper
import com.ppkitchen.data.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenDashboardScreen(
    onOpenIngredients: () -> Unit,
    onOpenDishes: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()

    val ingredients by db.ingredientDao().getAll().collectAsState(initial = emptyList())
    val dishes by db.dishDao().getAll().collectAsState(initial = emptyList())
    val templates by db.menuTemplateDao().getAll().collectAsState(initial = emptyList())

    var showExcelMenu by remember { mutableStateOf(false) }
    var showImportProgress by remember { mutableStateOf(false) }
    var importProgressText by remember { mutableStateOf("") }

    var showResultDialog by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }

    // --- ЛОКАЛЬНЫЙ ЭКСПОРТ ---
    val exportFullLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        ctx.contentResolver.openOutputStream(uri)?.use { out ->
                            ExcelHelper.exportFullBackup(out, db)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Полный бэкап сохранен", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // --- ИМПОРТ ---
    val importFullLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                showImportProgress = true
                try {
                    val result = withContext(Dispatchers.IO) {
                        ctx.contentResolver.openInputStream(uri)?.use { input ->
                            ExcelHelper.importFullBackup(input, db) { msg ->
                                importProgressText = msg
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        showImportProgress = false
                        importResult = result
                        showResultDialog = true
                    }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        showImportProgress = false
                        Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // --- ФУНКЦИЯ ПОДЕЛИТЬСЯ ---
    fun shareBackup() {
        scope.launch {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
                val fileName = "PP_Kitchen_Backup_${sdf.format(Date())}.xlsx"

                // Используем подпапку exports/, как указано в вашем file_paths.xml
                val exportsDir = File(ctx.cacheDir, "exports")
                if (!exportsDir.exists()) exportsDir.mkdirs()
                val file = File(exportsDir, fileName)

                withContext(Dispatchers.IO) {
                    file.outputStream().use { out ->
                        ExcelHelper.exportFullBackup(out, db)
                    }
                }

                val uri = FileProvider.getUriForFile(
                    ctx,
                    "${ctx.packageName}.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                ctx.startActivity(Intent.createChooser(intent, "Отправить базу через..."))
            } catch (e: Throwable) {
                Toast.makeText(ctx, "Ошибка при отправке: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showImportProgress) {
        Dialog(onDismissRequest = {}) {
            Card {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(importProgressText, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    if (showResultDialog && importResult != null) {
        val result = importResult!!
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = { Text("Импорт завершен!") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ингредиенты:", fontWeight = FontWeight.Bold)
                    Text("Добавлено: ${result.ingredientsAdded}, Обновлено: ${result.ingredientsUpdated}")

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    Text("Блюда:", fontWeight = FontWeight.Bold)
                    Text("Добавлено: ${result.dishesAdded}, Обновлено: ${result.dishesUpdated}")

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    Text("Меню:", fontWeight = FontWeight.Bold)
                    Text("Добавлено: ${result.templatesAdded}, Обновлено: ${result.templatesUpdated}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showResultDialog = false }) {
                    Text("Отлично")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CompactTopBar(
                title = "Кухня",
                onBack = null,
                actions = {
                    Box {
                        IconButton(onClick = { showExcelMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.List, "Excel", tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(
                            expanded = showExcelMenu,
                            onDismissRequest = { showExcelMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("💾 Сохранить на устройство") },
                                onClick = {
                                    showExcelMenu = false
                                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                    exportFullLauncher.launch("Full_Backup_${sdf.format(Date())}.xlsx")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📤 Поделиться бэкапом") },
                                onClick = {
                                    showExcelMenu = false
                                    shareBackup()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("📥 Импорт всей базы") },
                                onClick = {
                                    showExcelMenu = false
                                    importFullLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel"))
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DashboardCard(
                title = "Ингредиенты",
                subtitle = "В базе: ${ingredients.size} шт.",
                icon = Icons.Default.ShoppingBasket,
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = onOpenIngredients
            )
            DashboardCard(
                title = "База блюд",
                subtitle = "Рецептов: ${dishes.size} шт.",
                icon = Icons.Default.Restaurant,
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = onOpenDishes
            )
            DashboardCard(
                title = "Меню",
                subtitle = "Планов: ${templates.size} шт.",
                icon = Icons.Default.DateRange,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                onClick = onOpenMenu
            )
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}