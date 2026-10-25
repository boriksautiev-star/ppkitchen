package com.ppkitchen.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
    val amountStrings = remember { mutableStateMapOf<Long, String>().apply {
        existingDishIngredients.forEach { put(it.ingredientId, if (it.amount > 0) it.amount.toString() else "") }
    }}

    var showMultiIngredientPicker by remember { mutableStateOf(false) }

    val autoCalcData = remember(dishIngs.toList(), amountStrings.toMap()) {
        var totalKcal = 0.0
        var totalProt = 0.0
        var totalFat = 0.0
        var totalCarb = 0.0

        dishIngs.forEach { di ->
            val ing = ingredients.find { it.id == di.ingredientId }
            val amount = amountStrings[di.ingredientId]?.toDoubleOrNull() ?: 0.0
            if (ing != null && amount > 0) {
                val multiplier = if (ing.unit == "шт") amount else amount / 100.0
                totalKcal += ing.calories * multiplier
                totalProt += ing.protein * multiplier
                totalFat += ing.fat * multiplier
                totalCarb += ing.carbs * multiplier
            }
        }
        listOf(totalKcal, totalProt, totalFat, totalCarb)
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
                                        val amt = amountStrings[di.ingredientId]?.toDoubleOrNull() ?: 0.0
                                        if (amt > 0) {
                                            db.dishIngredientDao().insert(DishIngredient(dishId = dishId, ingredientId = di.ingredientId, amount = amt))
                                        }
                                    }
                                    onDismiss()
                                }
                            }
                        ) { Text("Сохранить") }
                    }
                )

                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(name, { name = it }, label = { Text("Название блюда*") }, modifier = Modifier.fillMaxWidth())

                    // Выбор группы блюда
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
                    Text("💡 БЖУ рассчитывается автоматически. Можно изменить вручную.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (hasMissingBju) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "⚠️ У одного или нескольких ингредиентов не заполнены БЖУ. Расчет может быть неточным.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(8.dp)
                            )
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
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            dishIngs.forEach { di ->
                                val ing = ingredients.find { it.id == di.ingredientId }
                                if (ing != null) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(ing.name, style = MaterialTheme.typography.bodyMedium)
                                            if (ing.calories > 0) {
                                                Text("${ing.calories} ккал/100${ing.unit}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            } else {
                                                Text("Нет БЖУ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                            }
                                        }

                                        OutlinedTextField(
                                            value = amountStrings[ing.id] ?: "",
                                            onValueChange = { input ->
                                                amountStrings[ing.id] = input.filter { it.isDigit() || it == '.' }
                                            },
                                            modifier = Modifier.width(120.dp),
                                            singleLine = true,
                                            suffix = { Text(ing.unit, style = MaterialTheme.typography.labelSmall) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                        )

                                        IconButton(
                                            onClick = {
                                                dishIngs.remove(di)
                                                amountStrings.remove(ing.id)
                                            }
                                        ) {
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
    }

    if (showMultiIngredientPicker) {
        MultiIngredientPickerDialog(
            ingredients = ingredients,
            categories = categories,
            onConfirm = { selectedIngs ->
                selectedIngs.forEach { ing ->
                    if (dishIngs.none { it.ingredientId == ing.id }) {
                        dishIngs.add(DishIngredient(dishId = dish?.id ?: 0, ingredientId = ing.id, amount = 0.0))
                        amountStrings[ing.id] = ""
                    }
                }
                showMultiIngredientPicker = false
            },
            onDismiss = { showMultiIngredientPicker = false }
        )
    }
}