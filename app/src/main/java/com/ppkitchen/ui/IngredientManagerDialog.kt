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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ppkitchen.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun IngredientManagerDialog(
    ingredients: List<Ingredient>,
    onDismiss: () -> Unit,
    onNavigateToOcr: () -> Unit
) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()

    val categories by db.ingredientCategoryDao().getAll().collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var editingIngredient by remember { mutableStateOf<Ingredient?>(null) }
    var showCategoryManager by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Все") }
    var sortMode by remember { mutableStateOf("name") }
    var showSortMenu by remember { mutableStateOf(false) }

    var isRefExpanded by remember { mutableStateOf(false) }

    var builtinCount by remember { mutableIntStateOf(0) }
    var customCount by remember { mutableIntStateOf(0) }

    fun refreshCounts() {
        scope.launch {
            builtinCount = db.foodReferenceDao().countBySource("builtin")
            customCount = db.foodReferenceDao().countBySource("custom")
        }
    }
    LaunchedEffect(Unit) { refreshCounts() }

    // --- СКАЧАТЬ ШАБЛОН ---
    val downloadTemplateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        ctx.contentResolver.openOutputStream(uri)?.use { out ->
                            ExcelHelper.createIngredientTemplate(out)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Шаблон Excel сохранен", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // --- ИМПОРТ EXCEL ---
    val importExcelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val (toUpdate, toInsert) = withContext(Dispatchers.IO) {
                        ctx.contentResolver.openInputStream(uri)?.use { input ->
                            ExcelHelper.importIngredients(input, ingredients)
                        } ?: Pair(emptyList(), emptyList())
                    }

                    for (ing in toUpdate) {
                        db.ingredientDao().update(ing)
                        // Обновляем и справочник (автозаполнение)
                        val ref = db.foodReferenceDao().findByName(ing.name)
                        db.foodReferenceDao().insert(FoodReference(
                            id = ref?.id ?: 0, name = ing.name, calories = ing.calories,
                            protein = ing.protein, fat = ing.fat, carbs = ing.carbs,
                            source = "custom", wastePercent = ing.wastePercent
                        ))
                    }
                    for (ing in toInsert) {
                        db.ingredientDao().insert(ing)
                        val ref = db.foodReferenceDao().findByName(ing.name)
                        db.foodReferenceDao().insert(FoodReference(
                            id = ref?.id ?: 0, name = ing.name, calories = ing.calories,
                            protein = ing.protein, fat = ing.fat, carbs = ing.carbs,
                            source = "custom", wastePercent = ing.wastePercent
                        ))
                    }

                    refreshCounts()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Импортировано: обновлено ${toUpdate.size}, добавлено ${toInsert.size}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Ошибка импорта Excel: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // --- ЭКСПОРТ EXCEL ---
    val exportExcelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        ctx.contentResolver.openOutputStream(uri)?.use { out ->
                            ExcelHelper.exportIngredients(out, ingredients)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Экспортировано в Excel", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Ошибка экспорта: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val filteredAndSorted = ingredients
        .filter { selectedCategory == "Все" || it.category == selectedCategory }
        .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
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
                        IconButton(onClick = onNavigateToOcr) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Сканер справочников")
                        }
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

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Поиск ингредиента") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, "Очистить")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { isRefExpanded = !isRefExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Справочник и Excel", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "Базовых: $builtinCount · Своих: $customCount",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (isRefExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }

                        if (isRefExpanded) {
                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = { downloadTemplateLauncher.launch("Шаблон_ингредиентов_PP_Kitchen.xlsx") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Скачать шаблон Excel", style = MaterialTheme.typography.labelMedium)
                            }

                            Spacer(Modifier.height(8.dp))

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { importExcelLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel")) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Импорт Excel", style = MaterialTheme.typography.labelMedium)
                                }
                                Button(
                                    onClick = {
                                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                        exportExcelLauncher.launch("ingredients_${sdf.format(java.util.Date())}.xlsx")
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Экспорт в Excel", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            db.foodReferenceDao().deleteBySource("builtin")
                                            FoodReferenceInitializer.initializeIfNeeded(ctx)
                                            refreshCounts()
                                            Toast.makeText(ctx, "Справочник восстановлен", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Сброс справочника к заводскому", style = MaterialTheme.typography.labelSmall)
                            }
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
                        Text("Ничего не найдено.", style = MaterialTheme.typography.bodyLarge)
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
                                        if (ing.pricePerKg > 0) {
                                            Text("${String.format(Locale.US, "%.2f", ing.pricePerKg)} ₽ $priceLabel", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                        } else {
                                            Text("Цена не указана", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                        }

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
                                source = "custom",
                                wastePercent = newIng.wastePercent
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

// ... CategoryManagerDialog и MultiIngredientPickerDialog остаются без изменений ...
// (Вставьте их сюда из предыдущего ответа, они не менялись)
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
    var searchQuery by remember { mutableStateOf("") }

    val filtered = ingredients
        .filter { selectedCategory == "Все" || it.category == selectedCategory }
        .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                CompactTopBar(
                    title = "Выбор ингредиентов",
                    onBack = onDismiss,
                    actions = {
                        TextButton(onClick = onDismiss) { Text("Отмена") }
                    }
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Поиск ингредиента") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, "Очистить")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
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

                if (filtered.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Ничего не найдено.", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        items(filtered, key = { it.id }) { ing ->
                            val isChecked = selectedIds.contains(ing.id)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedIds = if (isChecked) selectedIds - ing.id else selectedIds + ing.id
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        selectedIds = if (it) selectedIds + ing.id else selectedIds - ing.id
                                    }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(ing.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${ing.category} · ${ing.unit}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }

                Button(
                    onClick = {
                        val selectedIngs = ingredients.filter { selectedIds.contains(it.id) }
                        onConfirm(selectedIngs)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Text("Добавить выбранные (${selectedIds.size})")
                }
            }
        }
    }
}