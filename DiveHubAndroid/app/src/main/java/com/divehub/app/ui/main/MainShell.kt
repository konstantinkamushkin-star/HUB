package com.divehub.app.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.res.stringResource
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
import com.divehub.app.ui.admin.AdminGearManagementRoute
import com.divehub.app.ui.admin.AdminShopsManagementRoute
import com.divehub.app.ui.admin.AdminBookingManagementRoute
import com.divehub.app.ui.admin.AdminBookingCalendarRoute
import com.divehub.app.ui.admin.AdminAffiliatedSitesRoute
import com.divehub.app.ui.admin.AdminWebPanelRoute
import com.divehub.app.ui.inventory.InventoryRoute
import com.divehub.app.ui.inventory.InventoryItemDetailRoute
import com.divehub.app.ui.inventory.InventoryTicketDetailRoute
import com.divehub.app.ui.achievements.AchievementsRoute
import com.divehub.app.ui.notifications.NotificationsRoute
import com.divehub.app.ui.profile.CertificationsRoute
import com.divehub.app.ui.profile.DiveCenterAdminProfileRoute
import com.divehub.app.ui.profile.EditProfileRoute
import com.divehub.app.ui.profile.GearProfilesRoute
import com.divehub.app.ui.profile.HelpRoute
import com.divehub.app.ui.profile.MeasurementUnitsRoute
import com.divehub.app.ui.profile.MyBookingsRoute
import com.divehub.app.ui.profile.MyDiveSiteContributionsRoute
import com.divehub.app.ui.profile.NotificationSettingsRoute
import com.divehub.app.ui.profile.PrivacySettingsRoute
import com.divehub.app.ui.profile.SubscriptionRoute
import com.divehub.app.ui.profile.SettingsRoute
import com.divehub.app.ui.profile.InstructorPublicRoute
import com.divehub.app.ui.profile.UserProfileRoute
import com.divehub.app.ui.search.GlobalSearchRoute
import com.divehub.app.ui.statistics.StatisticsRoute
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.ui.Routes
import com.divehub.app.ui.navigation.resolveShellKind
import com.divehub.app.ui.booking.BookingWizardRoute
import com.divehub.app.ui.centers.DiveCenterPublicRoute
import com.divehub.app.ui.shops.ShopPublicRoute
import com.divehub.app.ui.chat.BusinessChatOpenRoute
import com.divehub.app.ui.help.AppSupportTopicRoute
import com.divehub.app.ui.help.SupportTicketFormRoute
import com.divehub.app.ui.diveeditor.DiveEditorRoute
import com.divehub.app.ui.explore.MapFullscreenRoute
import com.divehub.app.ui.trips.CenterManagedTripsRoute
import com.divehub.app.ui.trips.CreateTripRoute
import com.divehub.app.ui.trips.TripDetailRoute
import com.divehub.app.ui.trips.TripsRoute

@OptIn(ExperimentalMaterial3Api::class)
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
            route = InnerRoutes.DiveCenterPublic,
            arguments = listOf(navArgument("centerId") { type = NavType.StringType }),
        ) { entry ->
            val centerId = entry.arguments?.getString("centerId").orEmpty()
            if (centerId.isNotEmpty()) {
                DiveCenterPublicRoute(graph = graph, centerId = centerId, innerNav = innerNav)
            } else {
                LaunchedEffect(Unit) { innerNav.popBackStack() }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {}
            }
        }
        composable(
            route = InnerRoutes.ShopPublic,
            arguments = listOf(navArgument("shopId") { type = NavType.StringType }),
        ) { entry ->
            val shopId = entry.arguments?.getString("shopId").orEmpty()
            if (shopId.isNotEmpty()) {
                ShopPublicRoute(graph = graph, shopId = shopId, innerNav = innerNav)
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
        composable(
            route = InnerRoutes.InstructorPublic,
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType },
                navArgument("centerId") { type = NavType.StringType },
            ),
        ) { entry ->
            val uid = entry.arguments?.getString("userId").orEmpty()
            val cid = entry.arguments?.getString("centerId").orEmpty().takeIf { it.isNotBlank() && it != "-" }
            if (uid.isNotEmpty()) {
                InstructorPublicRoute(
                    graph = graph,
                    userId = uid,
                    centerId = cid,
                    innerNav = innerNav,
                )
            } else {
                LaunchedEffect(Unit) { innerNav.popBackStack() }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {}
            }
        }
        composable(InnerRoutes.Subscription) {
            SubscriptionRoute(graph = graph, sessionVm = vm, innerNav = innerNav)
        }
        composable(InnerRoutes.Certifications) {
            CertificationsRoute(graph = graph, sessionVm = vm, innerNav = innerNav)
        }
        composable(InnerRoutes.GearProfiles) {
            GearProfilesRoute(graph = graph, innerNav = innerNav)
        }
        composable(InnerRoutes.PrivacySettings) {
            PrivacySettingsRoute(graph = graph, innerNav = innerNav)
        }
        composable(InnerRoutes.NotificationSettings) {
            NotificationSettingsRoute(graph = graph, innerNav = innerNav)
        }
        composable(InnerRoutes.MeasurementUnits) {
            MeasurementUnitsRoute(graph = graph, innerNav = innerNav)
        }
        composable(InnerRoutes.MyDiveSiteContributions) {
            MyDiveSiteContributionsRoute(graph = graph, innerNav = innerNav)
        }
        composable(InnerRoutes.MyBookings) {
            MyBookingsRoute(graph = graph, innerNav = innerNav)
        }
        composable(InnerRoutes.DiveCenterAdminProfile) {
            DiveCenterAdminProfileRoute(graph = graph, innerNav = innerNav, sessionVm = vm)
        }
        composable(InnerRoutes.Help) {
            HelpRoute(
                innerNav = innerNav,
                onPartnerApplication = { rootNav.navigate(Routes.PartnerRegistration) },
            )
        }
        composable(InnerRoutes.AppSupportNewTopic) {
            AppSupportTopicRoute(graph = graph, innerNav = innerNav)
        }
        composable(
            route = InnerRoutes.SupportTicketForm,
            arguments = listOf(navArgument("category") { type = NavType.StringType }),
        ) { entry ->
            val category = entry.arguments?.getString("category").orEmpty()
            SupportTicketFormRoute(graph = graph, innerNav = innerNav, category = category)
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
        composable(InnerRoutes.AdminGearManagement) {
            AdminGearManagementRoute(graph = graph, innerNav = innerNav)
        }
        composable(InnerRoutes.AdminShopsManagement) {
            AdminShopsManagementRoute(graph = graph, innerNav = innerNav)
        }
        composable(InnerRoutes.AdminBookingManagement) {
            AdminBookingManagementRoute(graph = graph, innerNav = innerNav)
        }
        composable(InnerRoutes.AdminBookingCalendar) {
            AdminBookingCalendarRoute(graph = graph, innerNav = innerNav)
        }
        composable(InnerRoutes.AdminAffiliatedSites) {
            AdminAffiliatedSitesRoute(graph = graph, innerNav = innerNav)
        }
        composable(InnerRoutes.AdminWebPanel) {
            AdminWebPanelRoute(graph = graph, innerNav = innerNav, user = user)
        }
        composable(InnerRoutes.Inventory) {
            InventoryRoute(graph = graph, innerNav = innerNav)
        }
        composable(
            route = InnerRoutes.InventoryItemDetail,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
        ) { entry ->
            val itemId = entry.arguments?.getString("itemId").orEmpty()
            InventoryItemDetailRoute(graph = graph, innerNav = innerNav, itemId = itemId)
        }
        composable(
            route = InnerRoutes.InventoryTicketDetail,
            arguments = listOf(navArgument("ticketId") { type = NavType.StringType }),
        ) { entry ->
            val ticketId = entry.arguments?.getString("ticketId").orEmpty()
            InventoryTicketDetailRoute(graph = graph, innerNav = innerNav, ticketId = ticketId)
        }
        composable(InnerRoutes.MapFullscreen) {
            MapFullscreenRoute(graph = graph, innerNav = innerNav)
        }
        composable(
            route = InnerRoutes.BusinessChatOpen,
            arguments = listOf(
                navArgument("peerType") { type = NavType.StringType },
                navArgument("peerId") { type = NavType.StringType },
            ),
        ) { entry ->
            val peerType = entry.arguments?.getString("peerType").orEmpty()
            val peerId = entry.arguments?.getString("peerId").orEmpty()
            if (peerType.isNotBlank() && peerId.isNotBlank()) {
                BusinessChatOpenRoute(
                    graph = graph,
                    innerNav = innerNav,
                    peerType = peerType,
                    peerId = peerId,
                )
            } else {
                LaunchedEffect(Unit) { innerNav.popBackStack() }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {}
            }
        }
        composable(
            route = InnerRoutes.BookingWizard,
            arguments = listOf(
                navArgument("centerId") { type = NavType.StringType },
                navArgument("siteId") { type = NavType.StringType },
                navArgument("instructorId") { type = NavType.StringType },
                navArgument("courseId") { type = NavType.StringType },
            ),
        ) { entry ->
            fun pathSeg(key: String) =
                entry.arguments?.getString(key)?.takeIf { it.isNotBlank() && it != "-" }
            BookingWizardRoute(
                graph = graph,
                innerNav = innerNav,
                centerIdArg = pathSeg("centerId"),
                siteIdArg = pathSeg("siteId"),
                instructorIdArg = pathSeg("instructorId"),
                courseIdArg = pathSeg("courseId"),
            )
        }
        composable(InnerRoutes.DiveEditor) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.dive_editor_title)) },
                        navigationIcon = {
                            IconButton(onClick = { innerNav.popBackStack() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.common_back),
                                )
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    DiveEditorRoute()
                }
            }
        }
    }
}
