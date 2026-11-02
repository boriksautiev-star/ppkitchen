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
import androidx.compose.material.icons.filled.Edit
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
import com.ppkitchen.ocr.OcrMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.roundToInt

fun calculateDishCost(
    dishId: Long,
    dishIngredients: List<DishIngredient>,
    ingredients: List<Ingredient>,
    avgPrices: Map<Long, Double>
): Double {
    var cost = 0.0
    for (di in dishIngredients) {
        if (di.dishId == dishId) {
            val ing = ingredients.find { it.id == di.ingredientId }
            if (ing != null) {
                val waste = ing.wastePercent.coerceIn(0.0, 99.9)
                val bruttoAmount = if (waste > 0) di.amount / ((100.0 - waste) / 100.0) else di.amount
                val currentPrice = avgPrices[ing.id] ?: ing.pricePerKg

                cost += if (ing.unit == "шт") {
                    bruttoAmount * currentPrice
                } else {
                    (bruttoAmount / 1000.0) * currentPrice
                }
            }
        }
    }
    return cost
}

// НОВАЯ ФУНКЦИЯ ДЛЯ РАСЧЕТА БЖУ НА ЛЕТУ
data class BjuResult(val calories: Int, val protein: Int, val fat: Int, val carbs: Int)

fun calculateDishBju(
    dishId: Long,
    dishIngredients: List<DishIngredient>,
    ingredients: List<Ingredient>
): BjuResult {
    var kcal = 0.0; var prot = 0.0; var fat = 0.0; var carb = 0.0
    for (di in dishIngredients) {
        if (di.dishId == dishId) {
            val ing = ingredients.find { it.id == di.ingredientId }
            if (ing != null) {
                val grams = if (ing.unit == "шт") {
                    di.amount * (if (ing.weightPerPiece > 0) ing.weightPerPiece else 100.0)
                } else {
                    di.amount
                }
                val factor = grams / 100.0
                kcal += ing.calories * factor
                prot += ing.protein * factor
                fat += ing.fat * factor
                carb += ing.carbs * factor
            }
        }
    }
    return BjuResult(kcal.roundToInt(), prot.roundToInt(), fat.roundToInt(), carb.roundToInt())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishesScreen(
    onBack: () -> Unit,
    onNavigateToOcr: (OcrMode) -> Unit
) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()

    val dishes by db.dishDao().getAll().collectAsState(initial = emptyList())
    val ingredients by db.ingredientDao().getAll().collectAsState(initial = emptyList())
    val dishIngredients by db.dishIngredientDao().getAll().collectAsState(initial = emptyList())
    val dishCategories by db.dishCategoryDao().getAll().collectAsState(initial = emptyList())

    var avgPrices by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }

    LaunchedEffect(ingredients) {
        if (ingredients.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                val avgList = db.ingredientPriceDao().getAveragePricesSince(thirtyDaysAgo)
                avgPrices = avgList.associate { it.ingredientId to it.avgPrice }
            }
        }
    }

    var showCreateDish by remember { mutableStateOf(false) }
    var viewingDish by remember { mutableStateOf<Dish?>(null) }
    var editingDish by remember { mutableStateOf<Dish?>(null) }
    var dishToDelete by remember { mutableStateOf<Dish?>(null) }
    var selectedCategory by remember { mutableStateOf("Все") }
    var sortMode by remember { mutableStateOf("name") }
    var showSortMenu by remember { mutableStateOf(false) }

    val filtered = dishes.filter { selectedCategory == "Все" || it.category == selectedCategory }

    // Сортируем, используя динамический расчет БЖУ
    val filteredAndSorted = when (sortMode) {
        "calories_asc" -> filtered.sortedBy { calculateDishBju(it.id, dishIngredients, ingredients).calories }
        "calories_desc" -> filtered.sortedByDescending { calculateDishBju(it.id, dishIngredients, ingredients).calories }
        "cost_asc" -> filtered.sortedBy { calculateDishCost(it.id, dishIngredients, ingredients, avgPrices) }
        "cost_desc" -> filtered.sortedByDescending { calculateDishCost(it.id, dishIngredients, ingredients, avgPrices) }
        else -> filtered.sortedBy { it.name.lowercase() }
    }

    Scaffold(
        topBar = {
            CompactTopBar(
                title = "База блюд",
                onBack = onBack,
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, "Сортировка")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(text = { Text("По названию (А-Я)") }, onClick = { sortMode = "name"; showSortMenu = false })
                            DropdownMenuItem(text = { Text("По калориям (↑)") }, onClick = { sortMode = "calories_asc"; showSortMenu = false })
                            DropdownMenuItem(text = { Text("По калориям (↓)") }, onClick = { sortMode = "calories_desc"; showSortMenu = false })
                            DropdownMenuItem(text = { Text("По цене (↑)") }, onClick = { sortMode = "cost_asc"; showSortMenu = false })
                            DropdownMenuItem(text = { Text("По цене (↓)") }, onClick = { sortMode = "cost_desc"; showSortMenu = false })
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
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(selected = selectedCategory == "Все", onClick = { selectedCategory = "Все" }, label = { Text("Все") })
                dishCategories.forEach { cat ->
                    FilterChip(selected = selectedCategory == cat.name, onClick = { selectedCategory = cat.name }, label = { Text(cat.name) })
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
                        val cost = calculateDishCost(dish.id, dishIngredients, ingredients, avgPrices)
                        val bju = calculateDishBju(dish.id, dishIngredients, ingredients)

                        Card(Modifier.fillMaxWidth().clickable { viewingDish = dish }) {
                            Row(
                                Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(dish.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${bju.calories} ккал · Б${bju.protein} Ж${bju.fat} У${bju.carbs}",
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

    if (showCreateDish) {
        DishEditScreen(dish = null, ingredients = ingredients, existingDishIngredients = emptyList(), onSave = { _, _ -> }, onDismiss = { showCreateDish = false })
    }
    if (editingDish != null) {
        DishEditScreen(dish = editingDish, ingredients = ingredients, existingDishIngredients = dishIngredients.filter { it.dishId == editingDish!!.id }, onSave = { _, _ -> }, onDismiss = { editingDish = null })
    }
    if (viewingDish != null) {
        DishDetailScreen(
            dish = viewingDish!!, ingredients = ingredients, dishIngredients = dishIngredients, avgPrices = avgPrices,
            cost = calculateDishCost(viewingDish!!.id, dishIngredients, ingredients, avgPrices),
            bju = calculateDishBju(viewingDish!!.id, dishIngredients, ingredients), // Передаем БЖУ
            onEdit = { editingDish = viewingDish; viewingDish = null },
            onDelete = { dishToDelete = viewingDish; viewingDish = null },
            onDismiss = { viewingDish = null }
        )
    }
    if (dishToDelete != null) {
        AlertDialog(
            onDismissRequest = { dishToDelete = null },
            title = { Text("Удалить блюдо?") },
            text = { Text("Вы уверены, что хотите удалить «${dishToDelete!!.name}»?") },
            confirmButton = {
                TextButton(onClick = { scope.launch { db.dishIngredientDao().deleteForDish(dishToDelete!!.id); db.dishDao().delete(dishToDelete!!); dishToDelete = null } }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dishToDelete = null }) { Text("Отмена") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishDetailScreen(
    dish: Dish,
    ingredients: List<Ingredient>,
    dishIngredients: List<DishIngredient>,
    avgPrices: Map<Long, Double>,
    cost: Double,
    bju: BjuResult, // Новый параметр
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val dIngs = dishIngredients.filter { it.dishId == dish.id }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                CompactTopBar(
                    title = dish.name,
                    onBack = onDismiss,
                    actions = {
                        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Редактировать") }
                        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error) }
                    }
                )
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Группа: ${dish.category}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("Пищевая ценность:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            // Используем динамические значения
                            Text("Калории: ${bju.calories} ккал", style = MaterialTheme.typography.bodyMedium)
                            Text("Белки: ${bju.protein} г", style = MaterialTheme.typography.bodyMedium)
                            Text("Жиры: ${bju.fat} г", style = MaterialTheme.typography.bodyMedium)
                            Text("Углеводы: ${bju.carbs} г", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            Text("Себестоимость: ${String.format(Locale.US, "%.2f", cost)} ₽", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (dIngs.isNotEmpty()) {
                        Text("Технологическая карта:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                dIngs.forEach { di ->
                                    val ing = ingredients.find { it.id == di.ingredientId }
                                    if (ing != null) {
                                        val heatLoss = ing.getLossForMethod(di.cookingMethod)
                                        val output = if (heatLoss > 0) di.amount * ((100.0 - heatLoss) / 100.0) else di.amount
                                        val waste = ing.wastePercent.coerceIn(0.0, 99.9)
                                        val brutto = if (waste > 0) di.amount / ((100.0 - waste) / 100.0) else di.amount
                                        val outputStr = String.format(Locale.US, "%.1f", output)
                                        val nettoString = String.format(Locale.US, "%.1f", di.amount)
                                        val bruttoString = String.format(Locale.US, "%.1f", brutto)
                                        val displayUnit = when(ing.unit) { "кг" -> "г"; "л" -> "мл"; else -> ing.unit }
                                        val priceToShow = avgPrices[ing.id] ?: ing.pricePerKg

                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                            Column(Modifier.weight(1f)) {
                                                Text(ing.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                                Text("Способ обработки: ${di.cookingMethod}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                                if (waste > 0) Text("Холодные потери: $waste%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                                Text("Ср. цена: ${String.format(Locale.US, "%.2f", priceToShow)} ₽/кг", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Выход: $outputStr $displayUnit", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                Text("Нетто: $nettoString $displayUnit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                if (waste > 0) Text("Брутто: $bruttoString $displayUnit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
                }
            }
        }
    }
}