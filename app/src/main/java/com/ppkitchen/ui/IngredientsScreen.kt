package com.ppkitchen.ui

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
import com.ppkitchen.data.Ingredient
import com.ppkitchen.data.IngredientCategory
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientsScreen(
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()

    val ingredients by db.ingredientDao().getAll().collectAsState(initial = emptyList())
    val categories by db.ingredientCategoryDao().getAll().collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var editingIngredient by remember { mutableStateOf<Ingredient?>(null) }
    var showCategoryManager by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Все") }
    var sortMode by remember { mutableStateOf("name") }
    var showSortMenu by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            CompactTopBar(
                title = "Ингредиенты",
                onBack = onBack,
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
        },
        floatingActionButton = {
            // На случай, если пользователю удобнее добавлять через FAB
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Добавить ингредиент")
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
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
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
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

    if (showAddDialog) {
        IngredientEditScreen(
            ingredient = null,
            categories = categories,
            onSave = { newIng ->
                scope.launch {
                    db.ingredientDao().insert(newIng)
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