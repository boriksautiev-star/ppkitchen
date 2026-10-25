package com.ppkitchen.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ppkitchen.data.AppDatabase
import com.ppkitchen.data.FoodReference
import com.ppkitchen.data.FoodReferenceInitializer
import com.ppkitchen.data.Ingredient
import com.ppkitchen.data.IngredientCategory
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Locale

@Composable
fun IngredientManagerDialog(
    ingredients: List<Ingredient>,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()

    val categories by db.ingredientCategoryDao().getAll().collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var editingIngredient by remember { mutableStateOf<Ingredient?>(null) }
    var showCategoryManager by remember { mutableStateOf(false) }

    var selectedCategory by remember { mutableStateOf("Все") }
    var sortMode by remember { mutableStateOf("name") }
    var showSortMenu by remember { mutableStateOf(false) }

    var builtinCount by remember { mutableStateOf(0) }
    var customCount by remember { mutableStateOf(0) }

    fun refreshCounts() {
        scope.launch {
            builtinCount = db.foodReferenceDao().countBySource("builtin")
            customCount = db.foodReferenceDao().countBySource("custom")
        }
    }
    LaunchedEffect(Unit) { refreshCounts() }

    // Импорт из файла
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = ctx.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: return@launch

                    val arr = JSONArray(json)
                    val builtins = mutableListOf<FoodReference>()
                    val customs = mutableListOf<FoodReference>()

                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val source = o.optString("source", "builtin")
                        val ref = FoodReference(
                            name = o.getString("name"),
                            calories = o.optDouble("calories", 0.0),
                            protein = o.optDouble("protein", 0.0),
                            fat = o.optDouble("fat", 0.0),
                            carbs = o.optDouble("carbs", 0.0),
                            source = source
                        )
                        if (source == "custom") customs.add(ref) else builtins.add(ref)
                    }

                    db.foodReferenceDao().deleteBySource("builtin")
                    db.foodReferenceDao().insertAll(builtins)
                    db.foodReferenceDao().insertAll(customs)

                    refreshCounts()
                    Toast.makeText(ctx, "Импортировано: базовых ${builtins.size}, своих ${customs.size}", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Ошибка импорта: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Экспорт в файл
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val all = db.foodReferenceDao().getAllList()
                    val sb = StringBuilder()
                    sb.append("[\n")
                    all.forEachIndexed { i, ref ->
                        sb.append("{")
                        sb.append("\"name\":\"${jsonEscape(ref.name)}\",")
                        sb.append("\"calories\":${ref.calories},")
                        sb.append("\"protein\":${ref.protein},")
                        sb.append("\"fat\":${ref.fat},")
                        sb.append("\"carbs\":${ref.carbs},")
                        sb.append("\"source\":\"${ref.source}\"")
                        sb.append("}")
                        if (i < all.size - 1) sb.append(",")
                        sb.append("\n")
                    }
                    sb.append("]")

                    ctx.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(sb.toString().toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(ctx, "Экспортировано продуктов: ${all.size}", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Ошибка экспорта: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val filteredAndSorted = ingredients
        .filter { selectedCategory == "Все" || it.category == selectedCategory }
        .sortedWith(
            when (sortMode) {
                "price_asc" -> compareBy { it.pricePerKg }
                "price_desc" -> compareByDescending { it.pricePerKg }
                else -> compareBy { it.name.lowercase() }
            }
        )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                CompactTopBar(
                    title = "Ингредиенты",
                    onBack = onDismiss,
                    actions = {
                        IconButton(onClick = { showCategoryManager = true }) {
                            Icon(Icons.Default.Folder, "Категории")
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, "Сортировка")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("По названию (А-Я)") },
                                    onClick = { sortMode = "name"; showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("По цене (сначала дешевле)") },
                                    onClick = { sortMode = "price_asc"; showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("По цене (сначала дороже)") },
                                    onClick = { sortMode = "price_desc"; showSortMenu = false }
                                )
                            }
                        }
                        TextButton(onClick = { showAddDialog = true }) {
                            Text("Добавить")
                        }
                    }
                )

                // Блок справочника
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Справочник БЖУ", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Базовых: $builtinCount · Своих: $customCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Импорт", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                    exportLauncher.launch("food_reference_${sdf.format(java.util.Date())}.json")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Экспорт", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        db.foodReferenceDao().deleteBySource("builtin")
                                        FoodReferenceInitializer.initializeIfNeeded(ctx, db)
                                        refreshCounts()
                                        Toast.makeText(ctx, "Заводской справочник восстановлен", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Сбросить к заводскому", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == "Все",
                        onClick = { selectedCategory = "Все" },
                        label = { Text("Все") }
                    )
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat.name,
                            onClick = { selectedCategory = cat.name },
                            label = { Text(cat.name) }
                        )
                    }
                }

                if (filteredAndSorted.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("В этой категории ничего нет.", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredAndSorted, key = { it.id }) { ing ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editingIngredient = ing }
                            ) {
                                Row(
                                    Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(ing.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                        Text(ing.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        val priceLabel = when (ing.unit) {
                                            "г" -> "за 1 кг"
                                            "мл" -> "за 1 л"
                                            else -> "за 1 шт"
                                        }
                                        Text("${String.format(Locale.US, "%.2f", ing.pricePerKg)} ₽ $priceLabel", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)

                                        if (ing.calories > 0) {
                                            Text("БЖУ: ${ing.calories} ккал / Б${ing.protein} Ж${ing.fat} У${ing.carbs}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    IconButton(onClick = { scope.launch { db.ingredientDao().delete(ing) } }) {
                                        Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        IngredientEditScreen(
            ingredient = null,
            categories = categories,
            onSave = { newIng ->
                scope.launch {
                    db.ingredientDao().insert(newIng)
                    if (newIng.calories > 0 && db.foodReferenceDao().findByName(newIng.name) == null) {
                        db.foodReferenceDao().insert(
                            FoodReference(
                                name = newIng.name,
                                calories = newIng.calories,
                                protein = newIng.protein,
                                fat = newIng.fat,
                                carbs = newIng.carbs,
                                source = "custom"
                            )
                        )
                        refreshCounts()
                    }
                }
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (editingIngredient != null) {
        IngredientEditScreen(
            ingredient = editingIngredient,
            categories = categories,
            onSave = { updated ->
                scope.launch { db.ingredientDao().update(updated) }
                editingIngredient = null
            },
            onDismiss = { editingIngredient = null }
        )
    }

    if (showCategoryManager) {
        CategoryManagerDialog(
            onDismiss = { showCategoryManager = false }
        )
    }
}

private fun jsonEscape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

@Composable
fun CategoryManagerDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()

    val categories by db.ingredientCategoryDao().getAll().collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var editingCat by remember { mutableStateOf<IngredientCategory?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Группы ингредиентов") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(categories, key = { it.id }) { cat ->
                        Row(
                            Modifier.fillMaxWidth().clickable { editingCat = cat }.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = {
                                scope.launch {
                                    db.ingredientCategoryDao().renameCategoryInIngredients(cat.name, "Прочее")
                                    db.ingredientCategoryDao().delete(cat)
                                }
                            }) {
                                Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Добавить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )

    if (showAdd) {
        EditCategoryDialog(category = null, onSave = { newCat ->
            scope.launch { db.ingredientCategoryDao().insert(newCat) }
            showAdd = false
        }, onDismiss = { showAdd = false })
    }

    if (editingCat != null) {
        EditCategoryDialog(category = editingCat, onSave = { updatedCat ->
            scope.launch {
                db.ingredientCategoryDao().renameCategoryInIngredients(editingCat!!.name, updatedCat.name)
                db.ingredientCategoryDao().update(updatedCat)
            }
            editingCat = null
        }, onDismiss = { editingCat = null })
    }
}

@Composable
fun EditCategoryDialog(
    category: IngredientCategory?,
    onSave: (IngredientCategory) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "Новая группа" else "Переименовать группу") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(IngredientCategory(id = category?.id ?: 0, name = name.trim()))
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun MultiIngredientPickerDialog(
    ingredients: List<Ingredient>,
    categories: List<IngredientCategory>,
    onConfirm: (List<Ingredient>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var selectedCategory by remember { mutableStateOf("Все") }

    val filtered = ingredients.filter { selectedCategory == "Все" || it.category == selectedCategory }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите ингредиенты") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(selected = selectedCategory == "Все", onClick = { selectedCategory = "Все" }, label = { Text("Все", style = MaterialTheme.typography.bodySmall) })
                    categories.forEach { cat ->
                        FilterChip(selected = selectedCategory == cat.name, onClick = { selectedCategory = cat.name }, label = { Text(cat.name, style = MaterialTheme.typography.bodySmall) })
                    }
                }

                if (filtered.isEmpty()) {
                    Text("В этой категории пусто.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(filtered, key = { it.id }) { ing ->
                            val isChecked = selectedIds.contains(ing.id)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedIds = if (isChecked) selectedIds - ing.id else selectedIds + ing.id
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        selectedIds = if (it) selectedIds + ing.id else selectedIds - ing.id
                                    }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(ing.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(ing.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val selectedIngs = ingredients.filter { selectedIds.contains(it.id) }
                    onConfirm(selectedIngs)
                },
                enabled = selectedIds.isNotEmpty()
            ) {
                Text("Добавить (${selectedIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}