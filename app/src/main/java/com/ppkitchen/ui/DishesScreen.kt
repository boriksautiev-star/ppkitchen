package com.ppkitchen.ui

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ppkitchen.data.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishesScreen() {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()

    val dishes by db.dishDao().getAll().collectAsState(initial = emptyList())
    val ingredients by db.ingredientDao().getAll().collectAsState(initial = emptyList())
    val dishIngredients by db.dishIngredientDao().getAll().collectAsState(initial = emptyList())
    val dishCategories by db.dishCategoryDao().getAll().collectAsState(initial = emptyList())

    var showIngredientDialog by remember { mutableStateOf(false) }
    var showCreateDish by remember { mutableStateOf(false) }
    var showCategoryManager by remember { mutableStateOf(false) }

    var viewingDish by remember { mutableStateOf<Dish?>(null) }
    var editingDish by remember { mutableStateOf<Dish?>(null) }
    var dishToDelete by remember { mutableStateOf<Dish?>(null) }

    var selectedCategory by remember { mutableStateOf("Все") }
    var sortMode by remember { mutableStateOf("name") }
    var showSortMenu by remember { mutableStateOf(false) }

    fun calculateCost(dishId: Long): Double {
        val dIngs = dishIngredients.filter { it.dishId == dishId }
        var cost = 0.0
        dIngs.forEach { di ->
            val ing = ingredients.find { it.id == di.ingredientId }
            if (ing != null) {
                cost += if (ing.unit == "шт") {
                    di.amount * ing.pricePerKg
                } else {
                    (di.amount / 1000.0) * ing.pricePerKg
                }
            }
        }
        return cost
    }

    val filteredAndSorted = dishes
        .filter { selectedCategory == "Все" || it.category == selectedCategory }
        .sortedWith(
            when (sortMode) {
                "calories_asc" -> compareBy { it.calories }
                "calories_desc" -> compareByDescending { it.calories }
                "cost_asc" -> compareBy { calculateCost(it.id) }
                "cost_desc" -> compareByDescending { calculateCost(it.id) }
                else -> compareBy { it.name.lowercase() }
            }
        )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CompactTopBar(
                title = "База блюд",
                actions = {
                    IconButton(onClick = { showCategoryManager = true }) {
                        Icon(Icons.Default.Folder, "Группы блюд")
                    }
                    IconButton(onClick = { showIngredientDialog = true }) {
                        Icon(Icons.Default.ShoppingBasket, "Ингредиенты")
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
                                text = { Text("По калориям (возрастание)") },
                                onClick = { sortMode = "calories_asc"; showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("По калориям (убывание)") },
                                onClick = { sortMode = "calories_desc"; showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("По себестоимости (дешевле)") },
                                onClick = { sortMode = "cost_asc"; showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("По себестоимости (дороже)") },
                                onClick = { sortMode = "cost_desc"; showSortMenu = false }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDish = true }) {
                Icon(Icons.Default.Add, "Добавить блюдо")
            }
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            // Фильтры по категориям
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
                dishCategories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat.name,
                        onClick = { selectedCategory = cat.name },
                        label = { Text(cat.name) }
                    )
                }
            }

            if (filteredAndSorted.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("В этой категории блюд нет.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredAndSorted, key = { it.id }) { dish ->
                        val cost = calculateCost(dish.id)

                        Card(
                            Modifier
                                .fillMaxWidth()
                                .clickable { viewingDish = dish }
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(dish.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${dish.calories} ккал · Б${dish.protein} Ж${dish.fat} У${dish.carbs}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    String.format(Locale.US, "%.0f ₽", cost),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showIngredientDialog) {
        IngredientManagerDialog(
            ingredients = ingredients,
            onDismiss = { showIngredientDialog = false }
        )
    }

    if (showCategoryManager) {
        DishCategoryManagerDialog(
            onDismiss = { showCategoryManager = false }
        )
    }

    if (showCreateDish) {
        DishEditScreen(
            dish = null,
            ingredients = ingredients,
            existingDishIngredients = emptyList(),
            onSave = { _, _ -> },
            onDismiss = { showCreateDish = false }
        )
    }

    if (editingDish != null) {
        DishEditScreen(
            dish = editingDish,
            ingredients = ingredients,
            existingDishIngredients = dishIngredients.filter { it.dishId == editingDish!!.id },
            onSave = { _, _ -> },
            onDismiss = { editingDish = null }
        )
    }

    if (viewingDish != null) {
        val dish = viewingDish!!
        val cost = calculateCost(dish.id)
        val dIngs = dishIngredients.filter { it.dishId == dish.id }

        AlertDialog(
            onDismissRequest = { viewingDish = null },
            title = { Text(dish.name, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Группа: ${dish.category}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))

                    Text("Пищевая ценность:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Калории: ${dish.calories} ккал", style = MaterialTheme.typography.bodyMedium)
                    Text("Белки: ${dish.protein} г", style = MaterialTheme.typography.bodyMedium)
                    Text("Жиры: ${dish.fat} г", style = MaterialTheme.typography.bodyMedium)
                    Text("Углеводы: ${dish.carbs} г", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))

                    Text("Себестоимость: ${String.format(Locale.US, "%.2f", cost)} ₽", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)

                    if (dIngs.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text("Состав:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        dIngs.forEach { di ->
                            val ing = ingredients.find { it.id == di.ingredientId }
                            if (ing != null) {
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(ing.name, style = MaterialTheme.typography.bodyMedium)
                                    Text("${di.amount} ${ing.unit}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            dishToDelete = dish
                            viewingDish = null
                        }
                    ) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        onClick = {
                            editingDish = dish
                            viewingDish = null
                        }
                    ) {
                        Text("Редактировать")
                    }
                    TextButton(onClick = { viewingDish = null }) {
                        Text("Закрыть")
                    }
                }
            }
        )
    }

    if (dishToDelete != null) {
        AlertDialog(
            onDismissRequest = { dishToDelete = null },
            title = { Text("Удалить блюдо?") },
            text = { Text("Вы уверены, что хотите удалить «${dishToDelete!!.name}»? Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                db.dishIngredientDao().deleteForDish(dishToDelete!!.id)
                                db.dishDao().delete(dishToDelete!!)
                                dishToDelete = null
                            } catch (e: Exception) {
                                // Обработка ошибки
                            }
                        }
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { dishToDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun DishCategoryManagerDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()

    val categories by db.dishCategoryDao().getAll().collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var editingCat by remember { mutableStateOf<DishCategory?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Группы блюд") },
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
                                    db.dishCategoryDao().renameCategoryInDishes(cat.name, "Прочее")
                                    db.dishCategoryDao().delete(cat)
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
        EditDishCategoryDialog(category = null, onSave = { newCat ->
            scope.launch { db.dishCategoryDao().insert(newCat) }
            showAdd = false
        }, onDismiss = { showAdd = false })
    }

    if (editingCat != null) {
        EditDishCategoryDialog(category = editingCat, onSave = { updatedCat ->
            scope.launch {
                db.dishCategoryDao().renameCategoryInDishes(editingCat!!.name, updatedCat.name)
                db.dishCategoryDao().update(updatedCat)
            }
            editingCat = null
        }, onDismiss = { editingCat = null })
    }
}

@Composable
fun EditDishCategoryDialog(
    category: DishCategory?,
    onSave: (DishCategory) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "Новая группа" else "Переименовать группу") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(DishCategory(id = category?.id ?: 0, name = name.trim()))
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}