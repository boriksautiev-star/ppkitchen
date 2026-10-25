package com.ppkitchen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuTemplateEditScreen(
    template: MenuTemplate,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()

    val days by db.menuDayDao().getDaysForTemplate(template.id).collectAsState(initial = emptyList())
    val allDishes by db.dishDao().getAll().collectAsState(initial = emptyList())
    val allDishIngredients by db.dishIngredientDao().getAll().collectAsState(initial = emptyList())

    var editingDay by remember { mutableStateOf<MenuDay?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                CompactTopBar(
                    title = template.name,
                    onBack = onDismiss
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(days, key = { it.id }) { day ->
                        // Получаем блюда для этого дня
                        val dishLinks by db.menuDayDishDao().getDishesForDay(day.id).collectAsState(initial = emptyList())
                        val dayDishes = dishLinks.mapNotNull { link -> allDishes.find { it.id == link.dishId } }

                        Card(
                            Modifier.fillMaxWidth().clickable { editingDay = day }
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("День ${day.dayNumber}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Icon(Icons.Default.Add, "Добавить блюда", tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.height(4.dp))

                                if (dayDishes.isEmpty()) {
                                    Text("Блюда не выбраны", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                } else {
                                    // Суммарные Ккал и БЖУ за день
                                    var totalKcal = 0
                                    var totalProt = 0
                                    var totalFat = 0
                                    var totalCarb = 0
                                    dayDishes.forEach { d ->
                                        totalKcal += d.calories
                                        totalProt += d.protein
                                        totalFat += d.fat
                                        totalCarb += d.carbs
                                    }

                                    Text(
                                        "Итого за день: $totalKcal ккал · Б$totalProt Ж$totalFat У$totalCarb",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))

                                    dayDishes.forEach { dish ->
                                        Text("• ${dish.name} (${dish.calories} ккал)", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingDay != null) {
        DishPickerForDayDialog(
            day = editingDay!!,
            allDishes = allDishes,
            allDishIngredients = allDishIngredients,
            onDismiss = { editingDay = null }
        )
    }
}

@Composable
fun DishPickerForDayDialog(
    day: MenuDay,
    allDishes: List<Dish>,
    allDishIngredients: List<DishIngredient>,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()

    val existingLinks by db.menuDayDishDao().getDishesForDay(day.id).collectAsState(initial = emptyList())

    // Множество ID выбранных блюд
    var selectedIds by remember(existingLinks) {
        mutableStateOf(existingLinks.map { it.dishId }.toSet())
    }

    var selectedCategory by remember { mutableStateOf("Все") }
    val dishCategories by db.dishCategoryDao().getAll().collectAsState(initial = emptyList())

    val filtered = allDishes.filter { selectedCategory == "Все" || it.category == selectedCategory }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Блюда на День ${day.dayNumber}") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // Фильтры по категориям
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(selected = selectedCategory == "Все", onClick = { selectedCategory = "Все" }, label = { Text("Все", style = MaterialTheme.typography.bodySmall) })
                    dishCategories.forEach { cat ->
                        FilterChip(selected = selectedCategory == cat.name, onClick = { selectedCategory = cat.name }, label = { Text(cat.name, style = MaterialTheme.typography.bodySmall) })
                    }
                }

                if (filtered.isEmpty()) {
                    Text("В этой категории пусто.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(filtered, key = { it.id }) { dish ->
                            val isChecked = selectedIds.contains(dish.id)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedIds = if (isChecked) selectedIds - dish.id else selectedIds + dish.id
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        selectedIds = if (it) selectedIds + dish.id else selectedIds - dish.id
                                    }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(dish.name, style = MaterialTheme.typography.bodyMedium)
                                    Text("${dish.calories} ккал · Б${dish.protein} Ж${dish.fat} У${dish.carbs}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    scope.launch {
                        db.menuDayDishDao().deleteForDay(day.id)
                        selectedIds.forEach { dishId ->
                            db.menuDayDishDao().insert(MenuDayDish(menuDayId = day.id, dishId = dishId))
                        }
                        onDismiss()
                    }
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}