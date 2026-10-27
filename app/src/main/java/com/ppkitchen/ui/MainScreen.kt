package com.ppkitchen.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ppkitchen.data.AppDatabase
import com.ppkitchen.data.Ingredient
import com.ppkitchen.ocr.InvoiceRowDraft
import com.ppkitchen.ocr.OcrInvoiceScreen
import com.ppkitchen.ocr.OcrMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MainScreen(
    pendingRenewal: Pair<Long, Long>? = null,
    onConsumePendingRenewal: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val items = listOf(
        Triple("dashboard", "Отчёт", Icons.Default.Home),
        Triple("clients", "Клиенты", Icons.Default.Person),
        Triple("orders", "Заказы", Icons.Default.ShoppingCart),
        Triple("dishes", "Блюда", Icons.Default.Restaurant),
        Triple("expenses", "Расходы", Icons.Default.AttachMoney),
        Triple("cash", "Касса", Icons.Default.Savings)
    )

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showBottomBar = currentRoute in listOf("dashboard", "clients", "orders", "dishes", "expenses", "cash")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    items.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo("dashboard") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, label) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(padding)
        ) {
            composable("dashboard") {
                DashboardScreen(
                    onOpenCooks = { navController.navigate("cooks") },
                    onOpenSettings = { navController.navigate("settings") }
                )
            }
            composable("clients") {
                ClientsScreen(
                    onClientClick = { id -> navController.navigate("client/$id") }
                )
            }
            composable("orders") {
                OrdersScreen(
                    onOpenCouriers = { navController.navigate("couriers") }
                )
            }
            composable("dishes") {
                DishesScreen(
                    // ЯВНО УКАЗЫВАЕМ ТИП mode: OcrMode
                    onNavigateToOcr = { mode: OcrMode -> navController.navigate("ocr/${mode.name}") }
                )
            }
            composable("expenses") { ExpensesScreen() }
            composable("cash") { CashScreen() }
            composable("couriers") {
                CouriersScreen(onBack = { navController.popBackStack() })
            }
            composable("cooks") {
                CooksScreen(onBack = { navController.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCooks = { navController.navigate("cooks") }
                )
            }
            composable(
                route = "client/{clientId}",
                arguments = listOf(navArgument("clientId") { type = NavType.LongType })
            ) { backStackEntry ->
                val clientId = backStackEntry.arguments?.getLong("clientId") ?: 0L
                ClientDetailScreen(
                    clientId = clientId,
                    onBack = { navController.popBackStack() }
                )
            }

            // --- МАРШРУТ ДЛЯ СКАНЕРА ---
            composable(
                route = "ocr/{mode}",
                arguments = listOf(navArgument("mode") { type = NavType.StringType })
            ) { backStackEntry ->
                val modeStr = backStackEntry.arguments?.getString("mode") ?: "INVOICE"
                val mode = OcrMode.valueOf(modeStr)

                OcrInvoiceScreen(
                    mode = mode,
                    onBack = { navController.popBackStack() },
                    onSave = { drafts: List<InvoiceRowDraft> ->
                        scope.launch(Dispatchers.IO) {
                            val db = AppDatabase.get(context)
                            val dao = db.ingredientDao()
                            val existingList = dao.getAllOnce()
                            val existingMap = existingList.associateBy { it.name.lowercase() }

                            for (draft in drafts) {
                                if (draft.name.isBlank()) continue

                                val existingIng = existingMap[draft.name.lowercase()]

                                if (mode == OcrMode.INVOICE) {
                                    val parsedPrice = draft.price.replace(',', '.').toDoubleOrNull() ?: 0.0
                                    val parsedUnit = draft.unit.ifBlank { "г" }
                                    if (existingIng != null) {
                                        if (parsedPrice > 0) {
                                            dao.update(existingIng.copy(pricePerKg = parsedPrice, unit = parsedUnit))
                                        }
                                    } else {
                                        val newIng = Ingredient(
                                            name = draft.name,
                                            pricePerKg = parsedPrice,
                                            unit = parsedUnit,
                                            category = "Прочее"
                                        )
                                        dao.insert(newIng)
                                    }
                                } else {
                                    // Режим Справочника: обновляем или создаем с БЖУ
                                    val cal = draft.calories.replace(',', '.').toDoubleOrNull() ?: 0.0
                                    val prot = draft.protein.replace(',', '.').toDoubleOrNull() ?: 0.0
                                    val fat = draft.fat.replace(',', '.').toDoubleOrNull() ?: 0.0
                                    val carb = draft.carbs.replace(',', '.').toDoubleOrNull() ?: 0.0

                                    if (existingIng != null) {
                                        if (cal > 0 || prot > 0 || fat > 0 || carb > 0) {
                                            dao.update(existingIng.copy(
                                                calories = if (cal > 0) cal else existingIng.calories,
                                                protein = if (prot > 0) prot else existingIng.protein,
                                                fat = if (fat > 0) fat else existingIng.fat,
                                                carbs = if (carb > 0) carb else existingIng.carbs
                                            ))
                                        }
                                    } else {
                                        val newIng = Ingredient(
                                            name = draft.name,
                                            pricePerKg = 0.0,
                                            unit = "г",
                                            category = "Прочее",
                                            calories = cal,
                                            protein = prot,
                                            fat = fat,
                                            carbs = carb
                                        )
                                        dao.insert(newIng)
                                    }
                                }
                            }
                            withContext(Dispatchers.Main) {
                                navController.popBackStack()
                            }
                        }
                    }
                )
            }
        }
    }

    if (pendingRenewal != null) {
        RenewalDialog(
            clientId = pendingRenewal.first,
            orderId = pendingRenewal.second,
            onDismiss = onConsumePendingRenewal
        )
    }
}