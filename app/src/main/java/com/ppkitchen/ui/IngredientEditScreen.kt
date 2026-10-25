package com.ppkitchen.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ppkitchen.data.AppDatabase
import com.ppkitchen.data.FoodReference
import com.ppkitchen.data.Ingredient
import com.ppkitchen.data.IngredientCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientEditScreen(
    ingredient: Ingredient?,
    categories: List<IngredientCategory>,
    onSave: (Ingredient) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val isEdit = ingredient != null
    val keyboardController = LocalSoftwareKeyboardController.current

    var name by remember { mutableStateOf(ingredient?.name ?: "") }
    var priceText by remember { mutableStateOf(ingredient?.pricePerKg?.toString() ?: "") }
    var unit by remember { mutableStateOf(ingredient?.unit ?: "г") }
    var category by remember { mutableStateOf(ingredient?.category ?: "Прочее") }

    var kcalText by remember { mutableStateOf(if ((ingredient?.calories ?: 0.0) > 0) ingredient!!.calories.toString() else "") }
    var protText by remember { mutableStateOf(if ((ingredient?.protein ?: 0.0) > 0) ingredient!!.protein.toString() else "") }
    var fatText by remember { mutableStateOf(if ((ingredient?.fat ?: 0.0) > 0) ingredient!!.fat.toString() else "") }
    var carbText by remember { mutableStateOf(if ((ingredient?.carbs ?: 0.0) > 0) ingredient!!.carbs.toString() else "") }

    var showCategoryMenu by remember { mutableStateOf(false) }

    // Поиск и автодополнение
    var searchResults by remember { mutableStateOf<List<FoodReference>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }

    LaunchedEffect(name) {
        if (name.length > 1 && !isEdit) {
            searchResults = db.foodReferenceDao().search(name)
            showSuggestions = searchResults.isNotEmpty()
        } else {
            showSuggestions = false
        }
    }

    val priceFieldLabel = when (unit) {
        "г" -> "Цена за 1 КГ (₽)"
        "мл" -> "Цена за 1 ЛИТР (₽)"
        else -> "Цена за 1 ШТУКУ (₽)"
    }

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
                    title = if (isEdit) "Редактировать ингредиент" else "Новый ингредиент",
                    onBack = onDismiss,
                    actions = {
                        TextButton(
                            onClick = {
                                if (name.isBlank()) return@TextButton
                                onSave(Ingredient(
                                    id = ingredient?.id ?: 0,
                                    name = name.trim(),
                                    pricePerKg = priceText.toDoubleOrNull() ?: 0.0,
                                    unit = unit,
                                    category = category,
                                    calories = kcalText.toDoubleOrNull() ?: 0.0,
                                    protein = protText.toDoubleOrNull() ?: 0.0,
                                    fat = fatText.toDoubleOrNull() ?: 0.0,
                                    carbs = carbText.toDoubleOrNull() ?: 0.0
                                ))
                            }
                        ) {
                            Text(if (isEdit) "Сохранить" else "Создать")
                        }
                    }
                )

                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Поле поиска
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название продукта*") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Встроенный список подсказок (не крадет фокус)
                    if (showSuggestions) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 250.dp)
                            ) {
                                items(searchResults, key = { it.id }) { ref ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(ref.name, fontWeight = FontWeight.Medium)
                                                Text(
                                                    "${ref.calories} ккал · Б${ref.protein} Ж${ref.fat} У${ref.carbs}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            name = ref.name
                                            kcalText = if (ref.calories > 0) ref.calories.toString() else ""
                                            protText = if (ref.protein > 0) ref.protein.toString() else ""
                                            fatText = if (ref.fat > 0) ref.fat.toString() else ""
                                            carbText = if (ref.carbs > 0) ref.carbs.toString() else ""
                                            showSuggestions = false
                                            keyboardController?.hide() // Скрываем клавиатуру только после выбора
                                        }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }

                    // Выбор группы
                    Box {
                        OutlinedButton(
                            onClick = { showCategoryMenu = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Группа: $category")
                        }
                        DropdownMenu(
                            expanded = showCategoryMenu,
                            onDismissRequest = { showCategoryMenu = false }
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        category = cat.name
                                        showCategoryMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Выбор единицы измерения
                    Text("Единица измерения в рецепте", style = MaterialTheme.typography.labelMedium)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("г", "мл", "шт").forEach { u ->
                            FilterChip(
                                selected = unit == u,
                                onClick = { unit = u },
                                label = { Text(u) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Цена
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { input -> priceText = input.filter { it.isDigit() || it == '.' } },
                        label = { Text(priceFieldLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    // БЖУ
                    Text("Пищевая ценность на 100 ${if (unit == "шт") "грамм" else unit}", style = MaterialTheme.typography.titleSmall)

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = kcalText,
                            onValueChange = { input -> kcalText = input.filter { it.isDigit() || it == '.' } },
                            label = { Text("Ккал") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = protText,
                            onValueChange = { input -> protText = input.filter { it.isDigit() || it == '.' } },
                            label = { Text("Белки") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = fatText,
                            onValueChange = { input -> fatText = input.filter { it.isDigit() || it == '.' } },
                            label = { Text("Жиры") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = carbText,
                            onValueChange = { input -> carbText = input.filter { it.isDigit() || it == '.' } },
                            label = { Text("Углев.") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }

                    Text(
                        "💡 Подсказка: цена указывается за 1 кг/л/шт (брутто). " +
                                "Вес в рецептах блюд указывается чистый (нетто).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}