package com.callrecorder.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.callrecorder.app.core.utils.PermissionHelper
import com.callrecorder.app.core.utils.RoleHelper
import com.callrecorder.app.ui.navigation.Screen
import com.callrecorder.app.ui.screens.dashboard.DashboardScreen
import com.callrecorder.app.ui.screens.dashboard.DashboardViewModel
import com.callrecorder.app.ui.screens.search.SearchScreen
import com.callrecorder.app.ui.screens.settings.SettingsScreen
import com.callrecorder.app.ui.screens.settings.SettingsViewModel
import com.callrecorder.app.ui.theme.CallRecorderTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CallRecorderTheme {
                val context = LocalContext.current
                val dashboardViewModel: DashboardViewModel = viewModel()
                val settingsViewModel: SettingsViewModel = viewModel()

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { _ ->
                    dashboardViewModel.refreshPermissions()
                }

                val companionRoleLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { _ ->
                    dashboardViewModel.refreshPermissions()
                    settingsViewModel.refreshCompanionRoleStatus()
                }

                val requestCompanionRole = {
                    try {
                        val intent = RoleHelper.requestCallCompanionRoleIntent(context)
                        if (intent != null) {
                            companionRoleLauncher.launch(intent)
                        } else {
                            val settingsIntent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                            context.startActivity(settingsIntent)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Failed to launch role request", e)
                        try {
                            val settingsIntent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                            context.startActivity(settingsIntent)
                        } catch (_: Exception) {
                            PermissionHelper.openAppSettings(context)
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    if (!PermissionHelper.hasAllPermissions(context)) {
                        permissionLauncher.launch(PermissionHelper.getRequiredPermissions())
                    }
                }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            dashboardViewModel.refreshPermissions()
                            settingsViewModel.refreshBatteryOptimizationStatus()
                            settingsViewModel.refreshOverlayStatus()
                            settingsViewModel.refreshCompanionRoleStatus()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        dashboardViewModel = dashboardViewModel,
                        settingsViewModel = settingsViewModel,
                        onRequestPermissions = {
                            if (!PermissionHelper.hasAllPermissions(context)) {
                                permissionLauncher.launch(PermissionHelper.getRequiredPermissions())
                            } else {
                                PermissionHelper.openAppSettings(context)
                            }
                        },
                        onRequestCompanionRole = requestCompanionRole
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    dashboardViewModel: DashboardViewModel,
    settingsViewModel: SettingsViewModel,
    onRequestPermissions: () -> Unit,
    onRequestCompanionRole: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                viewModel = dashboardViewModel,
                onRequestPermissions = onRequestPermissions,
                onRequestCompanionRole = onRequestCompanionRole,
                onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                viewModel = dashboardViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onRequestCompanionRole = onRequestCompanionRole
            )
        }
    }
}
