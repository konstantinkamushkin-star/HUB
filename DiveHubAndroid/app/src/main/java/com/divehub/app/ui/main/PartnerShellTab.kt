package com.divehub.app.ui.main

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.divehub.app.R
import java.util.Locale

/**
 * Stable dive-center bottom bar keys — lockstep with iOS [PartnerShellTab] in `PartnerShellTab.swift`.
 */
object PartnerShellTab {
    const val DASHBOARD = "dashboard"
    const val EXPLORE = "explore"
    const val FEED = "feed"
    const val COURSES = "courses"
    const val TRIPS = "trips"
    const val PHOTO = "photo"
    const val SERVICES = "services"
    const val CHATS = "chats"
    const val PROFILE = "profile"

    val orderedKeys: List<String> = listOf(
        DASHBOARD, EXPLORE, FEED, COURSES, TRIPS, PHOTO, SERVICES, CHATS, PROFILE,
    )

    val bottomBarToggleableKeys: List<String> = orderedKeys.filter { it != DASHBOARD }

    fun fullBottomBarOrder(adminLayout: Map<String, Any?>?): List<String> {
        val baseOrder = (adminLayout?.get("bottomBarOrder") as? List<*>)
            ?.mapNotNull { it?.toString()?.trim()?.lowercase(Locale.ROOT) }
            ?.filter { it in orderedKeys }
            ?: emptyList()
        val merged = mutableListOf<String>()
        for (k in baseOrder) {
            if (k !in merged) merged.add(k)
        }
        for (k in orderedKeys) {
            if (k !in merged) {
                if (k == CHATS) {
                    val profileIndex = merged.indexOf(PROFILE)
                    if (profileIndex >= 0) merged.add(profileIndex, CHATS) else merged.add(k)
                } else {
                    merged.add(k)
                }
            }
        }
        return merged
    }

    fun visibleKeys(adminLayout: Map<String, Any?>?): List<String> {
        val hidden = (adminLayout?.get("bottomBarHiddenTabs") as? List<*>)
            ?.mapNotNull { it?.toString()?.trim()?.lowercase(Locale.ROOT) }
            ?.toSet()
            ?: emptySet()
        return fullBottomBarOrder(adminLayout).filter { it == DASHBOARD || it !in hidden }
    }

    @StringRes
    fun labelRes(key: String): Int = when (key.lowercase(Locale.ROOT)) {
        DASHBOARD -> R.string.partner_tab_home
        EXPLORE -> R.string.nav_explore
        FEED -> R.string.nav_feed
        COURSES -> R.string.partner_tab_courses
        TRIPS -> R.string.partner_tab_trips
        PHOTO -> R.string.partner_tab_photo
        SERVICES -> R.string.partner_tab_services
        CHATS -> R.string.partner_tab_chats
        PROFILE -> R.string.profile_title
        else -> R.string.app_name
    }

    fun icon(key: String): ImageVector = when (key.lowercase(Locale.ROOT)) {
        DASHBOARD -> Icons.Default.Home
        EXPLORE -> Icons.Default.Search
        FEED -> Icons.AutoMirrored.Filled.Article
        COURSES -> Icons.AutoMirrored.Filled.MenuBook
        TRIPS -> Icons.Default.DateRange
        PHOTO -> Icons.Default.AutoAwesome
        SERVICES -> Icons.Default.LocalOffer
        CHATS -> Icons.AutoMirrored.Filled.Chat
        PROFILE -> Icons.Default.AccountCircle
        else -> Icons.Default.Home
    }

    @Composable
    fun scrollTabItems(keys: List<String>): List<DiveHubScrollTabItem> =
        keys.mapIndexed { idx, key ->
            DiveHubScrollTabItem(
                index = idx,
                icon = icon(key),
                label = stringResource(labelRes(key)),
            )
        }
}

/** Instructor shell — iOS `InstructorTabView` (4 tabs). */
object InstructorShellTab {
    const val DASHBOARD = 0
    const val SCHEDULE = 1
    const val PHOTO = 2
    const val PROFILE = 3

    @Composable
    fun scrollTabItems(): List<DiveHubScrollTabItem> = listOf(
        DiveHubScrollTabItem(InstructorShellTab.DASHBOARD, Icons.Default.Home, stringResource(R.string.partner_tab_home)),
        DiveHubScrollTabItem(InstructorShellTab.SCHEDULE, Icons.Default.DateRange, stringResource(R.string.partner_tab_schedule)),
        DiveHubScrollTabItem(InstructorShellTab.PHOTO, Icons.Default.AutoAwesome, stringResource(R.string.partner_tab_photo)),
        DiveHubScrollTabItem(InstructorShellTab.PROFILE, Icons.Default.AccountCircle, stringResource(R.string.profile_title)),
    )
}

/** Shop shell — iOS `ShopTabView` (6 tabs). */
object ShopShellTab {
    const val DASHBOARD = 0
    const val MY_SHOP = 1
    const val PRODUCTS = 2
    const val ORDERS = 3
    const val ANALYTICS = 4
    const val PROFILE = 5

    @Composable
    fun scrollTabItems(): List<DiveHubScrollTabItem> = listOf(
        DiveHubScrollTabItem(DASHBOARD, Icons.Default.Home, stringResource(R.string.shop_tab_dashboard)),
        DiveHubScrollTabItem(MY_SHOP, Icons.Default.Store, stringResource(R.string.shop_tab_store_title)),
        DiveHubScrollTabItem(PRODUCTS, Icons.Default.LocalOffer, stringResource(R.string.shop_tab_products)),
        DiveHubScrollTabItem(ORDERS, Icons.Default.ShoppingCart, stringResource(R.string.shop_tab_orders)),
        DiveHubScrollTabItem(ANALYTICS, Icons.Default.BarChart, stringResource(R.string.partner_tab_analytics)),
        DiveHubScrollTabItem(PROFILE, Icons.Default.AccountCircle, stringResource(R.string.profile_title)),
    )
}

/** Super-admin shell — iOS `superAdminShell` (2 tabs). */
object SuperAdminShellTab {
    const val WEB_PANEL = 0
    const val PROFILE = 1

    @Composable
    fun scrollTabItems(): List<DiveHubScrollTabItem> = listOf(
        DiveHubScrollTabItem(WEB_PANEL, Icons.Default.Language, stringResource(R.string.partner_tab_web_panel)),
        DiveHubScrollTabItem(PROFILE, Icons.Default.AccountCircle, stringResource(R.string.profile_title)),
    )
}
