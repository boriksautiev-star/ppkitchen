package com.ppkitchen.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    pendingRenewal: Pair<Long, Long>? = null,
    onConsumePendingRenewal: () -> Unit = {}
) {
    val navController = rememberNavController()
    val items = listOf(
        Triple("dashboard", "Отчёт", Icons.Default.Home),
        Triple("clients", "Клиенты", Icons.Default.Person),
        Triple("orders", "Заказы", Icons.Default.ShoppingCart),
        Triple("expenses", "Расходы", Icons.Default.AttachMoney),
        Triple("cash", "Касса", Icons.Default.Savings)
    )

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showBottomBar = currentRoute in listOf("dashboard", "clients", "orders", "expenses", "cash")

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
                            label = { Text(label) }
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