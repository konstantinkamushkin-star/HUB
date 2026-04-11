package com.divehub.app.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.push.PushTokenRegistrar
import com.divehub.app.ui.admin.CenterInstructorsRoute
import com.divehub.app.ui.achievements.AchievementsRoute
import com.divehub.app.ui.notifications.NotificationsRoute
import com.divehub.app.ui.profile.EditProfileRoute
import com.divehub.app.ui.profile.FeaturePlaceholderRoute
import com.divehub.app.ui.profile.HelpRoute
import com.divehub.app.ui.profile.SettingsRoute
import com.divehub.app.ui.profile.UserProfileRoute
import com.divehub.app.ui.search.GlobalSearchRoute
import com.divehub.app.ui.statistics.StatisticsRoute
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.ui.Routes
import com.divehub.app.ui.navigation.resolveShellKind
import com.divehub.app.ui.trips.CenterManagedTripsRoute
import com.divehub.app.ui.trips.CreateTripRoute
import com.divehub.app.ui.trips.TripDetailRoute
import com.divehub.app.ui.trips.TripsRoute

@Composable
fun MainShell(
    graph: AppGraph,
    rootNav: NavController,
    onSessionExpired: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val innerNav = rememberNavController()
    val vm: SessionViewModel = viewModel(factory = SessionViewModel.factory(graph))
    val user by vm.user.collectAsState()
    val preferDiverShell by vm.preferDiverShell.collectAsState()
    val boot by vm.bootError.collectAsState()
    val context = LocalContext.current
    var askedPostNotifications by rememberSaveable { mutableStateOf(false) }
    val postNotificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(boot) {
        if (boot == "SESSION_EXPIRED") {
            onSessionExpired()
        }
    }

    LaunchedEffect(user?.id) {
        if (user == null) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedPostNotifications) {
            askedPostNotifications = true
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        PushTokenRegistrar.syncCurrentTokenIfNeeded(graph)
    }

    NavHost(
        navController = innerNav,
        startDestination = InnerRoutes.Home,
    ) {
        composable(InnerRoutes.Home) {
            AppHome(
                kind = user.resolveShellKind(preferDiverShell = preferDiverShell),
                graph = graph,
                innerNav = innerNav,
                rootNav = rootNav,
                sessionVm = vm,
                onLoggedOut = onLoggedOut,
            )
        }
        composable(InnerRoutes.Trips) {
            TripsRoute(graph = graph, innerNav = innerNav)
        }
        composable(InnerRoutes.TripCreate) {
            CreateTripRoute(graph = graph, innerNav = innerNav, editingTripId = null)
        }
        composable(
            route = InnerRoutes.TripEdit,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
        ) { entry ->
            val tripId = entry.arguments?.getString("tripId").orEmpty()
            if (tripId.isNotEmpty()) {
                CreateTripRoute(graph = graph, innerNav = innerNav, editingTripId = tripId)
            } else {
                LaunchedEffect(Unit) { innerNav.popBackStack() }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {}
            }
        }
        composable(
            route = InnerRoutes.CenterInstructors,
            arguments = listOf(navArgument("centerId") { type = NavType.StringType }),
        ) { entry ->
            val centerId = entry.arguments?.getString("centerId").orEmpty()
            if (centerId.isNotEmpty()) {
                CenterInstructorsRoute(graph = graph, centerId = centerId, innerNav = innerNav)
            } else {
                LaunchedEffect(Unit) { innerNav.popBackStack() }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {}
            }
        }
        composable(
            route = InnerRoutes.CenterTrips,
            arguments = listOf(navArgument("centerId") { type = NavType.StringType }),
        ) { entry ->
            val centerId = entry.arguments?.getString("centerId").orEmpty()
            if (centerId.isNotEmpty()) {
                CenterManagedTripsRoute(graph = graph, centerId = centerId, innerNav = innerNav)
            } else {
                LaunchedEffect(Unit) { innerNav.popBackStack() }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {}
            }
        }
        composable(
            route = InnerRoutes.TripDetail,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
        ) { entry ->
            val tripId = entry.arguments?.getString("tripId").orEmpty()
            if (tripId.isNotEmpty()) {
                TripDetailRoute(graph = graph, tripId = tripId, innerNav = innerNav)
            } else {
                LaunchedEffect(Unit) { innerNav.popBackStack() }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {}
            }
        }
        composable(InnerRoutes.EditProfile) {
            EditProfileRoute(graph = graph, innerNav = innerNav, sessionVm = vm)
        }
        composable(InnerRoutes.Search) {
            GlobalSearchRoute(graph = graph, innerNav = innerNav)
        }
        composable(
            route = InnerRoutes.UserProfile,
            arguments = listOf(navArgument("userId") { type = NavType.StringType }),
        ) { entry ->
            val userId = entry.arguments?.getString("userId").orEmpty()
            if (userId.isNotEmpty()) {
                UserProfileRoute(graph = graph, userId = userId, innerNav = innerNav)
            } else {
                LaunchedEffect(Unit) { innerNav.popBackStack() }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {}
            }
        }
        composable(InnerRoutes.Subscription) {
            FeaturePlaceholderRoute(
                titleRes = R.string.screen_subscription,
                bodyRes = R.string.feature_placeholder_body,
                innerNav = innerNav,
            )
        }
        composable(InnerRoutes.Certifications) {
            FeaturePlaceholderRoute(
                titleRes = R.string.screen_certifications,
                bodyRes = R.string.feature_placeholder_body,
                innerNav = innerNav,
            )
        }
        composable(InnerRoutes.GearProfiles) {
            FeaturePlaceholderRoute(
                titleRes = R.string.screen_gear_profiles,
                bodyRes = R.string.feature_placeholder_body,
                innerNav = innerNav,
            )
        }
        composable(InnerRoutes.PrivacySettings) {
            FeaturePlaceholderRoute(
                titleRes = R.string.screen_privacy_settings,
                bodyRes = R.string.feature_placeholder_body,
                innerNav = innerNav,
            )
        }
        composable(InnerRoutes.NotificationSettings) {
            FeaturePlaceholderRoute(
                titleRes = R.string.screen_notification_settings,
                bodyRes = R.string.feature_placeholder_body,
                innerNav = innerNav,
            )
        }
        composable(InnerRoutes.MeasurementUnits) {
            FeaturePlaceholderRoute(
                titleRes = R.string.screen_measurement_units,
                bodyRes = R.string.feature_placeholder_body,
                innerNav = innerNav,
            )
        }
        composable(InnerRoutes.Help) {
            HelpRoute(
                innerNav = innerNav,
                onPartnerApplication = { rootNav.navigate(Routes.PartnerRegistration) },
            )
        }
        composable(InnerRoutes.Notifications) {
            NotificationsRoute(graph = graph, innerNav = innerNav)
        }
        composable(InnerRoutes.Settings) {
            SettingsRoute(graph = graph, sessionVm = vm, innerNav = innerNav)
        }
        composable(InnerRoutes.Statistics) {
            StatisticsRoute(graph = graph, innerNav = innerNav)
        }
        composable(InnerRoutes.Achievements) {
            AchievementsRoute(graph = graph, innerNav = innerNav)
        }
    }
}
