package com.divehub.app.ui.main

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.ui.navigation.AppShellKind

@Composable
fun AppHome(
    kind: AppShellKind,
    graph: AppGraph,
    innerNav: NavController,
    rootNav: NavController,
    sessionVm: SessionViewModel,
    onLoggedOut: () -> Unit,
) {
    when (kind) {
        AppShellKind.DIVER -> DiverAppShell(
            graph = graph,
            innerNav = innerNav,
            rootNav = rootNav,
            sessionVm = sessionVm,
            onLoggedOut = onLoggedOut,
        )
        AppShellKind.ADMIN,
        AppShellKind.SHOP,
        AppShellKind.INSTRUCTOR,
        -> PartnerAppShell(
            kind = kind,
            graph = graph,
            innerNav = innerNav,
            rootNav = rootNav,
            sessionVm = sessionVm,
            onLoggedOut = onLoggedOut,
        )
    }
}
