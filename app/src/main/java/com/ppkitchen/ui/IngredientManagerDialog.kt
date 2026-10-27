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
import com.ppkitchen.data.AppDatabase
import com.ppkitchen.data.FoodReference
import com.ppkitchen.data.FoodReferenceInitializer
import com.ppkitchen.data.Ingredient
import com.ppkitchen.data.IngredientCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.reader.ReadableWorkbook
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
                            val wb = Workbook(out, "PP Kitchen", "1.0")
                            val ws = wb.newWorksheet("Ингредиенты")

                            ws.value(0, 0, "Название")
                            ws.value(0, 1, "Цена")
                            ws.value(0, 2, "Категория")
                            ws.value(0, 3, "Ед.изм")
                            ws.value(0, 4, "Ккал")
                            ws.value(0, 5, "Белки")
                            ws.value(0, 6, "Жиры")
                            ws.value(0, 7, "Углеводы")
                            ws.value(0, 8, "Отходы(%)")
                            ws.value(0, 9, "Варка(%)")
                            ws.value(0, 10, "Жарка(%)")
                            ws.value(0, 11, "Запекание(%)")
                            ws.value(0, 12, "Тушение(%)")

                            // Пример
                            ws.value(1, 0, "Курица грудка")
                            ws.value(1, 1, 350.0)
                            ws.value(1, 2, "Мясо")
                            ws.value(1, 3, "г")
                            ws.value(1, 4, 113.0)
                            ws.value(1, 5, 23.6)
                            ws.value(1, 6, 1.9)
                            ws.value(1, 7, 0.4)
                            ws.value(1, 8, 5.0)
                            ws.value(1, 9, 25.0)
                            ws.value(1, 10, 35.0)
                            ws.value(1, 11, 40.0)
                            ws.value(1, 12, 30.0)

                            wb.finish()
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Шаблон Excel сохранен", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
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
                    val toInsert = mutableListOf<Ingredient>()
                    val toUpdate = mutableListOf<Ingredient>()

                    withContext(Dispatchers.IO) {
                        ctx.contentResolver.openInputStream(uri)?.use { input ->
                            ReadableWorkbook(input).use { wb ->
                                val sheet = wb.firstSheet
                                sheet?.openStream()?.use { rows ->
                                    rows.skip(1).forEach { row ->
                                        try {
                                            val name = row.getCell(0)?.asString()?.trim() ?: ""
                                            if (name.isNotBlank()) {
                                                val price = row.getCell(1)?.asNumber()?.toDouble() ?: 0.0
                                                val cat = row.getCell(2)?.asString()?.trim() ?: "Прочее"
                                                val unit = row.getCell(3)?.asString()?.trim() ?: "г"
                                                val kcal = row.getCell(4)?.asNumber()?.toDouble() ?: 0.0
                                                val prot = row.getCell(5)?.asNumber()?.toDouble() ?: 0.0
                                                val fat = row.getCell(6)?.asNumber()?.toDouble() ?: 0.0
                                                val carb = row.getCell(7)?.asNumber()?.toDouble() ?: 0.0
                                                val waste = row.getCell(8)?.asNumber()?.toDouble() ?: 0.0
                                                val lossBoil = row.getCell(9)?.asNumber()?.toDouble() ?: 0.0
                                                val lossFry = row.getCell(10)?.asNumber()?.toDouble() ?: 0.0
                                                val lossBake = row.getCell(11)?.asNumber()?.toDouble() ?: 0.0
                                                val lossStew = row.getCell(12)?.asNumber()?.toDouble() ?: 0.0

                                                val existing = ingredients.find { it.name.equals(name, ignoreCase = true) }
                                                val newIng = Ingredient(
                                                    id = existing?.id ?: 0,
                                                    name = name,
                                                    pricePerKg = price,
                                                    category = cat,
                                                    unit = unit,
                                                    calories = kcal,
                                                    protein = prot,
                                                    fat = fat,
                                                    carbs = carb,
                                                    wastePercent = waste,
                                                    lossBoiling = lossBoil,
                                                    lossFrying = lossFry,
                                                    lossBaking = lossBake,
                                                    lossStewing = lossStew
                                                )
                                                if (existing != null) toUpdate.add(newIng) else toInsert.add(newIng)
                                            }
                                        } catch (_: Exception) { }
                                    }
                                }
                            }
                        }
                    }

                    toUpdate.forEach { db.ingredientDao().update(it) }
                    toInsert.forEach { db.ingredientDao().insert(it) }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Импортировано: обновлено ${toUpdate.size}, добавлено ${toInsert.size}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
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
                            val wb = Workbook(out, "PP Kitchen", "1.0")
                            val ws = wb.newWorksheet("Ингредиенты")

                            ws.value(0, 0, "Название")
                            ws.value(0, 1, "Цена")
                            ws.value(0, 2, "Категория")
                            ws.value(0, 3, "Ед.изм")
                            ws.value(0, 4, "Ккал")
                            ws.value(0, 5, "Белки")
                            ws.value(0, 6, "Жиры")
                            ws.value(0, 7, "Углеводы")
                            ws.value(0, 8, "Отходы(%)")
                            ws.value(0, 9, "Варка(%)")
                            ws.value(0, 10, "Жарка(%)")
                            ws.value(0, 11, "Запекание(%)")
                            ws.value(0, 12, "Тушение(%)")

                            ingredients.forEachIndexed { index, ing ->
                                val row = index + 1
                                ws.value(row, 0, ing.name)
                                ws.value(row, 1, ing.pricePerKg)
                                ws.value(row, 2, ing.category)
                                ws.value(row, 3, ing.unit)
                                ws.value(row, 4, ing.calories)
                                ws.value(row, 5, ing.protein)
                                ws.value(row, 6, ing.fat)
                                ws.value(row, 7, ing.carbs)
                                ws.value(row, 8, ing.wastePercent)
                                ws.value(row, 9, ing.lossBoiling)
                                ws.value(row, 10, ing.lossFrying)
                                ws.value(row, 11, ing.lossBaking)
                                ws.value(row, 12, ing.lossStewing)
                            }
                            wb.finish()
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Экспортировано в Excel", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Ошибка экспорта: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // --- ИМПОРТ JSON (без изменений) ---
    val importJsonLauncher = rememberLauncherForActivityResult(
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
                        val name = o.getString("name")

                        val waste = if (o.has("wastePercent")) {
                            o.optDouble("wastePercent", 0.0)
                        } else {
                            FoodReferenceInitializer.getSuggestedWaste(name).toDoubleOrNull() ?: 0.0
                        }

                        val ref = FoodReference(
                            name = name,
                            calories = o.optDouble("calories", 0.0),
                            protein = o.optDouble("protein", 0.0),
                            fat = o.optDouble("fat", 0.0),
                            carbs = o.optDouble("carbs", 0.0),
                            source = source,
                            wastePercent = waste
                        )
                        if (source == "custom") customs.add(ref) else builtins.add(ref)
                    }

                    db.foodReferenceDao().deleteBySource("builtin")
                    db.foodReferenceDao().insertAll(builtins)
                    db.foodReferenceDao().insertAll(customs)

                    refreshCounts()
                    Toast.makeText(ctx, "Импортировано JSON: базовых ${builtins.size}, своих ${customs.size}", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Ошибка импорта: ${e.message}", Toast.LENGTH_LONG).show()
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

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Импорт JSON", style = MaterialTheme.typography.labelSmall)
                                }
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
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Сброс к заводскому", style = MaterialTheme.typography.labelSmall)
                                }
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