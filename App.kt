package com.example.salaryapp.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.salaryapp.AppViewModel
import com.example.salaryapp.Stage

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun App(vm: AppViewModel) {
    when (vm.stage) {
        Stage.LOGIN -> LoginScreen(vm)
        Stage.CHANGE_PASSWORD -> ChangePasswordScreen(vm, forced = true, modifier = Modifier.safeDrawingPadding()) {}
        Stage.MAIN -> MainScaffold(vm)
    }
}

@Composable
private fun MainScaffold(vm: AppViewModel) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val tabs = listOf(
        Tab("home", "Home", Icons.Default.Home),
        Tab("payslips", "Payslips", Icons.Default.Receipt),
        Tab("profile", "Profile", Icons.Default.AccountCircle)
    )
    Scaffold(bottomBar = {
        if (route in tabs.map { it.route }) {
            NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    }) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") {
                HomeScreen(vm,
                    onOpen = { nav.navigate("payslip/$it") },
                    onSeeAll = { nav.navigate("payslips") })
            }
            composable("payslips") { PayslipListScreen(vm) { nav.navigate("payslip/$it") } }
            composable("payslip/{month}") { e ->
                PayslipDetailScreen(vm, e.arguments?.getString("month") ?: "", onBack = { nav.popBackStack() })
            }
            composable("profile") { ProfileScreen(vm, onChangePassword = { nav.navigate("password") }) }
            composable("password") {
                ChangePasswordScreen(vm, forced = false) { nav.popBackStack() }
            }
        }
    }
}
