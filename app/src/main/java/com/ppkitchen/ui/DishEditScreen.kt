package com.ppkitchen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ppkitchen.data.*
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishEditScreen(
    dish: Dish?,
    ingredients: List<Ingredient>,
    existingDishIngredients: List<DishIngredient>,
    onSave: (Dish, List<DishIngredient>) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()
    val isEdit = dish != null

    val categories by db.ingredientCategoryDao().getAll().collectAsState(initial = emptyList())
    val dishCategories by db.dishCategoryDao().getAll().collectAsState(initial = emptyList())

    var name by remember { mutableStateOf(dish?.name ?: "") }
    var category by remember { mutableStateOf(dish?.category ?: "Прочее") }
    var calories by remember { mutableStateOf(dish?.calories?.toString() ?: "") }
    var protein by remember { mutableStateOf(dish?.protein?.toString() ?: "") }
    var fat by remember { mutableStateOf(dish?.fat?.toString() ?: "") }
    var carbs by remember { mutableStateOf(dish?.carbs?.toString() ?: "") }

    var showDishCategoryMenu by remember { mutableStateOf(false) }

    val dishIngs = remember { mutableStateListOf<DishIngredient>().apply { addAll(existingDishIngredients) } }

    // ИНИЦИАЛИЗАЦИЯ: Переводим сохраненное НЕТТО в ВЫХОД для отображения пользователю
    val amountStrings = remember { mutableStateMapOf<Long, String>().apply {
        existingDishIngredients.forEach { di ->
            val ing = ingredients.find { it.id == di.ingredientId }
            if (ing != null) {
                val heatLoss = FoodReferenceInitializer.getHeatLossForMethod(ing.name, di.cookingMethod)
                val output = if (heatLoss > 0) di.amount * ((100.0 - heatLoss) / 100.0) else di.amount
                put(di.ingredientId, if (output > 0) String.format(Locale.US, "%.1f", output) else "")
            } else {
                put(di.ingredientId, if (di.amount > 0) di.amount.toString() else "")
            }
        }
    }}

    var showMultiIngredientPicker by remember { mutableStateOf(false) }

    val autoCalcData = remember(dishIngs.toList(), amountStrings.toMap(), ingredients) {
        var totalKcal = 0.0
        var totalProt = 0.0
        var totalFat = 0.0
        var totalCarb = 0.0
        var totalOutputWeight = 0.0

        dishIngs.forEach { di ->
            val ing = ingredients.find { it.id == di.ingredientId }
            val output = amountStrings[di.ingredientId]?.toDoubleOrNull() ?: 0.0
            if (ing != null && output > 0) {
                // БЖУ считаем по НЕТТО (переводим из Выхода)
                val heatLoss = FoodReferenceInitializer.getHeatLossForMethod(ing.name, di.cookingMethod)
                val netto = if (heatLoss > 0) output / ((100.0 - heatLoss) / 100.0) else output

                val multiplier = if (ing.unit == "шт") netto else netto / 100.0
                totalKcal += ing.calories * multiplier
                totalProt += ing.protein * multiplier
                totalFat += ing.fat * multiplier
                totalCarb += ing.carbs * multiplier

                totalOutputWeight += output
            }
        }
        listOf(totalKcal, totalProt, totalFat, totalCarb, totalOutputWeight)
    }

    LaunchedEffect(autoCalcData) {
        if (autoCalcData.any { it > 0 }) {
            calories = autoCalcData[0].roundToInt().toString()
            protein = autoCalcData[1].roundToInt().toString()
            fat = autoCalcData[2].roundToInt().toString()
            carbs = autoCalcData[3].roundToInt().toString()
        }
    }

    val hasMissingBju = remember(dishIngs.toList(), ingredients) {
        dishIngs.any { di ->
            val ing = ingredients.find { it.id == di.ingredientId }
            ing != null && ing.calories == 0.0 && ing.protein == 0.0 && ing.fat == 0.0 && ing.carbs == 0.0
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                CompactTopBar(
                    title = if (isEdit) "Редактировать блюдо" else "Новое блюдо",
                    onBack = onDismiss,
                    actions = {
                        TextButton(
                            onClick = {
                                if (name.isBlank()) return@TextButton
                                val newDish = Dish(
                                    id = dish?.id ?: 0,
                                    name = name.trim(),
                                    category = category.trim(),
                                    calories = calories.toIntOrNull() ?: 0,
                                    protein = protein.toIntOrNull() ?: 0,
                                    fat = fat.toIntOrNull() ?: 0,
                                    carbs = carbs.toIntOrNull() ?: 0
                                )
                                scope.launch {
                                    val dishId = if (isEdit) {
                                        db.dishDao().update(newDish)
                                        newDish.id
                                    } else {
                                        db.dishDao().insert(newDish)
                                    }

                                    db.dishIngredientDao().deleteForDish(dishId)
                                    dishIngs.forEach { di ->
                                        val output = amountStrings[di.ingredientId]?.toDoubleOrNull() ?: 0.0
                                        val ing = ingredients.find { it.id == di.ingredientId }
                                        if (ing != null && output > 0) {
                                            // ПЕРЕВОДИМ ВЫХОД В НЕТТО ДЛЯ СОХРАНЕНИЯ В БАЗУ
                                            val heatLoss = FoodReferenceInitializer.getHeatLossForMethod(ing.name, di.cookingMethod)
                                            val amtNetto = if (heatLoss > 0) output / ((100.0 - heatLoss) / 100.0) else output

                                            db.dishIngredientDao().insert(
                                                DishIngredient(
                                                    dishId = dishId,
                                                    ingredientId = di.ingredientId,
                                                    amount = amtNetto, // Сохраняем НЕТТО
                                                    cookingMethod = di.cookingMethod
                                                )
                                            )
                                        }
                                    }
                                    onDismiss()
                                }
                            }
                        ) { Text("Сохранить") }
                    }
                )

                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(name, { name = it }, label = { Text("Название блюда*") }, modifier = Modifier.fillMaxWidth())

                    Box {
                        OutlinedButton(onClick = { showDishCategoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Группа блюда: $category")
                        }
                        DropdownMenu(expanded = showDishCategoryMenu, onDismissRequest = { showDishCategoryMenu = false }) {
                            dishCategories.forEach { cat ->
                                DropdownMenuItem(text = { Text(cat.name) }, onClick = { category = cat.name; showDishCategoryMenu = false })
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(calories, { input -> calories = input.filter { it.isDigit() } }, label = { Text("Ккал") }, modifier = Modifier.weight(1f), readOnly = false)
                        OutlinedTextField(protein, { input -> protein = input.filter { it.isDigit() } }, label = { Text("Б") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(fat, { input -> fat = input.filter { it.isDigit() } }, label = { Text("Ж") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(carbs, { input -> carbs = input.filter { it.isDigit() } }, label = { Text("У") }, modifier = Modifier.weight(1f))
                    }

                    if (autoCalcData[4] > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Выход готового блюда:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${String.format(Locale.US, "%.1f", autoCalcData[4])} г",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Text("💡 Введите вес готового продукта (в тарелке). Программа сама рассчитает, сколько нужно взять сырья (Брутто).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (hasMissingBju) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                            Text("⚠️ У одного или нескольких ингредиентов не заполнены БЖУ. Расчет может быть неточным.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(8.dp))
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Состав блюда", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Button(onClick = { showMultiIngredientPicker = true }) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Добавить")
                        }
                    }

                    if (dishIngs.isEmpty()) {
                        Text("Ингредиенты не добавлены. Нажмите «Добавить».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            dishIngs.forEach { di ->
                                val ing = ingredients.find { it.id == di.ingredientId }
                                if (ing != null) {
                                    val output = amountStrings[ing.id]?.toDoubleOrNull() ?: 0.0

                                    // Считаем НЕТТО и БРУТТО
                                    val heatLoss = FoodReferenceInitializer.getHeatLossForMethod(ing.name, di.cookingMethod)
                                    val netto = if (heatLoss > 0 && output > 0) output / ((100.0 - heatLoss) / 100.0) else output

                                    val waste = ing.wastePercent.coerceIn(0.0, 99.9)
                                    val brutto = if (waste > 0 && netto > 0) netto / ((100.0 - waste) / 100.0) else netto

                                    val nettoStr = String.format(Locale.US, "%.1f", netto)
                                    val bruttoStr = String.format(Locale.US, "%.1f", brutto)

                                    val displayUnit = when (ing.unit) {
                                        "кг" -> "г"
                                        "л" -> "мл"
                                        else -> ing.unit
                                    }

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    ) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(ing.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                IconButton(onClick = { dishIngs.remove(di); amountStrings.remove(ing.id) }) {
                                                    Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }

                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedTextField(
                                                    value = amountStrings[ing.id] ?: "",
                                                    onValueChange = { input -> amountStrings[ing.id] = input.filter { it.isDigit() || it == '.' } },
                                                    modifier = Modifier.weight(1f),
                                                    singleLine = true,
                                                    label = { Text("Выход", style = MaterialTheme.typography.labelSmall) },
                                                    suffix = { Text(displayUnit, style = MaterialTheme.typography.labelSmall) },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                                )

                                                var showMethodMenu by remember { mutableStateOf(false) }
                                                Box(modifier = Modifier.weight(1f)) {
                                                    OutlinedButton(
                                                        onClick = { showMethodMenu = true },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                                    ) {
                                                        Text(di.cookingMethod, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                                        Icon(Icons.Default.ArrowDropDown, null)
                                                    }
                                                    DropdownMenu(
                                                        expanded = showMethodMenu,
                                                        onDismissRequest = { showMethodMenu = false }
                                                    ) {
                                                        listOf("Сырой", "Варка", "Жарка", "Запекание", "Тушение").forEach { method ->
                                                            DropdownMenuItem(
                                                                text = { Text(method) },
                                                                onClick = {
                                                                    val index = dishIngs.indexOf(di)
                                                                    if (index != -1) {
                                                                        dishIngs[index] = di.copy(cookingMethod = method)
                                                                    }
                                                                    showMethodMenu = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            if (output > 0) {
                                                Row(
                                                    Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column {
                                                        Text("Нетто (в кастрюлю): $nettoStr $displayUnit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text("Брутто (со склада): $bruttoStr $displayUnit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                    }
                                                    if (heatLoss > 0) {
                                                        Text("Потери: $heatLoss%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMultiIngredientPicker) {
        MultiIngredientPickerDialog(
            ingredients = ingredients,
            categories = categories,
            onConfirm = { selectedIngs ->
                selectedIngs.forEach { ing ->
                    if (dishIngs.none { it.ingredientId == ing.id }) {
                        dishIngs.add(DishIngredient(dishId = dish?.id ?: 0, ingredientId = ing.id, amount = 0.0, cookingMethod = "Сырой"))
                        amountStrings[ing.id] = ""
                    }
                }
                showMultiIngredientPicker = false
            },
            onDismiss = { showMultiIngredientPicker = false }
        )
    }
}