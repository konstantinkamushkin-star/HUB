package com.divehub.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.divehub.app.AppGraph
import com.divehub.app.ui.auth.ChangePasswordRoute
import com.divehub.app.ui.auth.ForgotPasswordRoute
import com.divehub.app.ui.auth.LoginRoute
import com.divehub.app.ui.auth.PartnerRegistrationRoute
import com.divehub.app.ui.auth.RegisterRoute
import com.divehub.app.ui.main.MainShell
import com.divehub.app.ui.onboarding.OnboardingRoute
import com.divehub.app.ui.splash.SplashRoute

@Composable
fun RootNav(graph: AppGraph) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.Splash) {
        composable(Routes.Splash) {
            SplashRoute(nav, graph)
        }
        composable(Routes.Onboarding) {
            OnboardingRoute(nav, graph)
        }
        composable(Routes.Login) {
            LoginRoute(nav, graph)
        }
        composable(Routes.ForgotPassword) {
            ForgotPasswordRoute(nav, graph)
        }
        composable(Routes.Register) {
            RegisterRoute(nav, graph)
        }
        composable(Routes.PartnerRegistration) {
            PartnerRegistrationRoute(nav, graph)
        }
        composable(Routes.ChangePassword) {
            ChangePasswordRoute(nav, graph)
        }
        composable(Routes.Main) {
            MainShell(
                graph = graph,
                rootNav = nav,
                onSessionExpired = {
                    nav.navigate(Routes.Login) {
                        popUpTo(Routes.Main) { inclusive = true }
                    }
                },
                onLoggedOut = {
                    nav.navigate(Routes.Login) {
                        popUpTo(Routes.Main) { inclusive = true }
                    }
                },
            )
        }
    }
}
