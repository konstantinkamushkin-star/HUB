package com.divehub.app.ui.main

/**
 * Diver bottom carousel tab indices — lockstep with iOS [DiverTabView] in `MainTabView.swift`.
 * Map is not a tab; use [com.divehub.app.ui.navigation.InnerRoutes.MapFullscreen] from Explore.
 */
object DiverTabIndices {
    const val EXPLORE = 0
    const val FEED = 1
    const val LOGBOOK = 2
    const val TRIPS = 3
    const val SOCIAL = 4
    const val CHAT = 5
    const val DIVE_EDITOR = 6
    const val PROFILE_WITH_EDITOR = 7
    const val PROFILE_NO_EDITOR = 6

    fun profileTab(diveEditorEnabled: Boolean): Int =
        if (diveEditorEnabled) PROFILE_WITH_EDITOR else PROFILE_NO_EDITOR
}
