package com.ziv.reminders

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ziv.reminders.ui.activity.ActivityScreen
import com.ziv.reminders.ui.activity.ActivityViewModel
import com.ziv.reminders.ui.dashboard.DashboardScreen
import com.ziv.reminders.ui.dashboard.DashboardViewModel
import com.ziv.reminders.ui.exercise.ExerciseCounterScreen
import com.ziv.reminders.ui.exercise.ExerciseViewModel
import com.ziv.reminders.ui.theme.RemindersTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        requestBatteryOptimizationExemptionIfNeeded()
        val container = (application as RemindersApp).container
        setContent {
            RemindersTheme {
                val navController = rememberNavController()
                val dashboardViewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container))
                val exerciseViewModel: ExerciseViewModel = viewModel(factory = ExerciseViewModel.factory(container))
                val activityViewModel: ActivityViewModel = viewModel(factory = ActivityViewModel.factory(container))

                NavHost(navController = navController, startDestination = "dashboard") {
                    composable("dashboard") {
                        // Fires every time this destination re-enters composition —
                        // including after popping back from the Exercise/Activity flows, not
                        // just on cold start — so the dashboard's rows never show stale data.
                        LaunchedEffect(Unit) { dashboardViewModel.refresh() }
                        DashboardScreen(
                            viewModel = dashboardViewModel,
                            onOpenExercise = { navController.navigate("exerciseCounter") },
                            onOpenActivity = { navController.navigate("activity") },
                        )
                    }
                    composable("exerciseCounter") {
                        ExerciseCounterScreen(
                            viewModel = exerciseViewModel,
                            onOpenStats = { navController.navigate("activity") },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("activity") {
                        ActivityScreen(
                            activityViewModel = activityViewModel,
                            exerciseViewModel = exerciseViewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // No persisted "already asked" state, no denied-state banner — re-checks and
    // re-prompts every launch until actually granted, same shape as the notification
    // permission check above. Some OEMs (Samsung, Xiaomi) aggressively kill background
    // alarms/WorkManager without this exemption.
    private fun requestBatteryOptimizationExemptionIfNeeded() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        }
    }
}
