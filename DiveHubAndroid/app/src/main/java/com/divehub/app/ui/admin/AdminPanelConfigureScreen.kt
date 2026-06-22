package com.divehub.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.AuthRepository
import com.divehub.app.data.local.AdminDashboardLayout
import com.divehub.app.data.local.AdminDashboardLayout.Companion.KEY_CAL
import com.divehub.app.data.local.AdminDashboardLayout.Companion.KEY_QUICK
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.ui.main.SessionViewModel
import com.divehub.app.ui.theme.IosDesign
import java.util.ArrayList
import java.util.LinkedHashSet
import java.util.Locale
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelConfigureBottomSheet(
    graph: AppGraph,
    user: UserDto,
    sessionVm: SessionViewModel,
    onDismiss: () -> Unit,
) {
    val authRepo = remember { AuthRepository(graph) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    @Suppress("UNCHECKED_CAST")
    val adminMap: Map<String, Any?> =
        (user.diverProfile?.get("adminDashboardLayout") as? Map<*, *>)
            ?.mapNotNull { (k, v) -> (k as? String)?.let { it to v } }
            ?.toMap()
            ?: emptyMap()

    val ios = parseAdminIosHomeConfig(user.diverProfile)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = IosDesign.Profile.pageBackground,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(IosDesign.Profile.pageBackground)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    onClick = onDismiss,
                ) {
                    Text(
                        stringResource(R.string.common_done),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Text(
                    stringResource(R.string.admin_dashboard_customize),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(72.dp))
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    stringResource(R.string.admin_panel_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                AdminPanelSectionLabel(stringResource(R.string.admin_panel_section_bottom))
                IosGrouped {
                    for ((ii, k) in PartnerTabToggleableOrder.withIndex()) {
                        if (ii > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )
                        }
                        val hidden = isTabHidden(k, adminMap)
                        AdminPanelToggleRow(
                            label = tabKeyLabelString(k),
                            checked = !hidden,
                            onChecked = { on ->
                                scope.launch {
                                    runCatching {
                                        authRepo.patchAdminDashboardMap { m -> setTabHiddenInMap(m, k, hidden = !on) }
                                    }.onSuccess { sessionVm.onUserUpdated(it) }
                                }
                            },
                        )
                    }
                }
                Text(
                    stringResource(R.string.admin_panel_bottom_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (visibleTabOrder(adminMap).size > 1) {
                    AdminPanelSectionLabel(stringResource(R.string.admin_panel_section_tab_order))
                    Text(
                        stringResource(R.string.admin_panel_drag_reorder),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IosGrouped {
                        val vis = visibleTabOrder(adminMap)
                        vis.forEachIndexed { index, tabKey ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp, horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    tabKeyLabelString(tabKey),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (index == 0) return@IconButton
                                            scope.launch {
                                                val newVis = vis.toMutableList()
                                                newVis.add(index - 1, newVis.removeAt(index))
                                                runCatching {
                                                    authRepo.patchAdminDashboardMap { applyBottomBarOrderFromVisible(it, newVis) }
                                                }.onSuccess { sessionVm.onUserUpdated(it) }
                                            }
                                        },
                                        enabled = index > 0,
                                    ) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                                    IconButton(
                                        onClick = {
                                            if (index >= vis.lastIndex) return@IconButton
                                            scope.launch {
                                                val newVis = vis.toMutableList()
                                                newVis.add(index + 1, newVis.removeAt(index))
                                                runCatching {
                                                    authRepo.patchAdminDashboardMap { applyBottomBarOrderFromVisible(it, newVis) }
                                                }.onSuccess { sessionVm.onUserUpdated(it) }
                                            }
                                        },
                                        enabled = index < vis.lastIndex,
                                    ) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                                    Icon(
                                        Icons.Filled.DragHandle,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                AdminPanelSectionLabel(stringResource(R.string.admin_panel_section_quick))
                IosGrouped {
                    val options = quickActionOptions(adminMap)
                    ios.quickActionTargets.forEachIndexed { index, t ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )
                        }
                        key("$index/$t") {
                            QuickActionTargetRow(
                                target = t,
                                options = options,
                                onSetTarget = { newKey ->
                                    scope.launch {
                                        runCatching {
                                            val arr = ios.quickActionTargets.toMutableList()
                                            if (index in arr.indices) arr[index] = newKey.lowercase(Locale.ROOT)
                                            authRepo.patchAdminDashboardMap { m -> m["quickActionTargets"] = ArrayList(arr) }
                                        }.onSuccess { sessionVm.onUserUpdated(it) }
                                    }
                                },
                                onMoveUp = {
                                    if (index == 0) return@QuickActionTargetRow
                                    scope.launch {
                                        val arr = ios.quickActionTargets.toMutableList()
                                        if (index in arr.indices) {
                                            val tmp = arr[index - 1]
                                            arr[index - 1] = arr[index]
                                            arr[index] = tmp
                                        }
                                        runCatching { authRepo.patchAdminDashboardMap { m -> m["quickActionTargets"] = ArrayList(arr) } }
                                            .onSuccess { sessionVm.onUserUpdated(it) }
                                    }
                                },
                                onMoveDown = {
                                    if (index >= ios.quickActionTargets.lastIndex) return@QuickActionTargetRow
                                    scope.launch {
                                        val arr = ios.quickActionTargets.toMutableList()
                                        if (index + 1 in arr.indices) {
                                            val tmp = arr[index + 1]
                                            arr[index + 1] = arr[index]
                                            arr[index] = tmp
                                        }
                                        runCatching { authRepo.patchAdminDashboardMap { m -> m["quickActionTargets"] = ArrayList(arr) } }
                                            .onSuccess { sessionVm.onUserUpdated(it) }
                                    }
                                },
                                onRemove = {
                                    scope.launch {
                                        val arr = ios.quickActionTargets.toMutableList()
                                        if (index in arr.indices) arr.removeAt(index)
                                        if (arr.isNotEmpty()) {
                                            runCatching { authRepo.patchAdminDashboardMap { m -> m["quickActionTargets"] = ArrayList(arr) } }
                                                .onSuccess { sessionVm.onUserUpdated(it) }
                                        }
                                    }
                                },
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            val fallback = options.firstOrNull() ?: "dashboard"
                            scope.launch {
                                val arr = ios.quickActionTargets.toMutableList()
                                if (arr.isEmpty()) {
                                    arr.add("instructors")
                                    arr.add("services")
                                } else {
                                    arr.add(fallback)
                                }
                                runCatching { authRepo.patchAdminDashboardMap { m -> m["quickActionTargets"] = ArrayList(arr) } }
                                    .onSuccess { sessionVm.onUserUpdated(it) }
                            }
                        },
                        modifier = Modifier.padding(8.dp),
                    ) { Text(stringResource(R.string.admin_panel_add_action), color = MaterialTheme.colorScheme.primary) }
                }

                AdminPanelSectionLabel(stringResource(R.string.admin_panel_block_order_ios))
                IosGrouped {
                    val bOrder = iosBlockOrderKeys(adminMap)
                    bOrder.forEachIndexed { index, bid ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp, 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (bid == "quick") stringResource(R.string.admin_home_quick_actions)
                                else stringResource(R.string.admin_home_calendar),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (index == 0) return@IconButton
                                        scope.launch {
                                            val a = bOrder.toMutableList()
                                            a.add(index - 1, a.removeAt(index))
                                            runCatching { authRepo.patchAdminDashboardMap { patchSectionOrderIosFromQuickCal(a, it, user) } }
                                                .onSuccess { sessionVm.onUserUpdated(it) }
                                        }
                                    },
                                    enabled = index > 0,
                                ) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                                IconButton(
                                    onClick = {
                                        if (index >= bOrder.lastIndex) return@IconButton
                                        scope.launch {
                                            val a = bOrder.toMutableList()
                                            a.add(index + 1, a.removeAt(index))
                                            runCatching { authRepo.patchAdminDashboardMap { patchSectionOrderIosFromQuickCal(a, it, user) } }
                                                .onSuccess { sessionVm.onUserUpdated(it) }
                                        }
                                    },
                                    enabled = index < bOrder.lastIndex,
                                ) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                                Icon(
                                    Icons.Filled.DragHandle,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                AdminPanelSectionLabel(stringResource(R.string.admin_panel_help))
                IosGrouped {
                    Column(Modifier.padding(10.dp, 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.admin_panel_help_1),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )
                        Text(
                            stringResource(R.string.admin_panel_help_2),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )
                        Text(
                            stringResource(R.string.admin_panel_help_3),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                AdminPanelSectionLabel(stringResource(R.string.admin_panel_visibility))
                IosGrouped {
                    AdminPanelToggleRow(
                        label = stringResource(R.string.admin_panel_show_quick),
                        checked = ios.showQuick,
                        onChecked = { on ->
                            scope.launch {
                                runCatching { authRepo.patchAdminDashboardMap { m -> m[KEY_QUICK] = on } }
                                    .onSuccess { sessionVm.onUserUpdated(it) }
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    AdminPanelToggleRow(
                        label = stringResource(R.string.admin_panel_show_calendar),
                        checked = ios.showCal,
                        onChecked = { on ->
                            scope.launch {
                                runCatching { authRepo.patchAdminDashboardMap { m -> m[KEY_CAL] = on } }
                                    .onSuccess { sessionVm.onUserUpdated(it) }
                            }
                        },
                    )
                }

                IosGrouped {
                    TextButton(
                        onClick = {
                            scope.launch {
                                runCatching { authRepo.resetAdminDashboardLayout() }
                                    .onSuccess { sessionVm.onUserUpdated(it); onDismiss() }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.admin_dashboard_reset),
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionTargetRow(
    target: String,
    options: List<String>,
    onSetTarget: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp, 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.admin_panel_open) + ": " + targetLabelString(target),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { menu = true }
                    .padding(4.dp, 2.dp),
            )
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
            ) {
                for (o in options) {
                    DropdownMenuItem(
                        text = { Text(targetLabelString(o)) },
                        onClick = {
                            menu = false
                            onSetTarget(o)
                        },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMoveUp) { Icon(Icons.Filled.KeyboardArrowUp, null) }
            IconButton(onClick = onMoveDown) { Icon(Icons.Filled.KeyboardArrowDown, null) }
            TextButton(
                onClick = onRemove,
            ) { Text(stringResource(R.string.admin_panel_remove), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun targetLabelString(t: String) = when (t.lowercase(Locale.ROOT)) {
    "instructors" -> stringResource(R.string.admin_quick_action_instructors)
    "services" -> stringResource(R.string.partner_tab_services)
    "dashboard" -> stringResource(R.string.partner_tab_home)
    "feed" -> stringResource(R.string.nav_feed)
    "chats" -> stringResource(R.string.partner_tab_chats)
    "explore" -> stringResource(R.string.nav_explore)
    "courses" -> stringResource(R.string.partner_tab_courses)
    "trips" -> stringResource(R.string.partner_tab_trips)
    "photo" -> stringResource(R.string.partner_tab_photo)
    "profile" -> stringResource(R.string.profile_title)
    else -> t
}

@Composable
private fun tabKeyLabelString(key: String) = when (key) {
    "dashboard" -> stringResource(R.string.partner_tab_home)
    "explore" -> stringResource(R.string.nav_explore)
    "feed" -> stringResource(R.string.nav_feed)
    "courses" -> stringResource(R.string.partner_tab_courses)
    "trips" -> stringResource(R.string.partner_tab_trips)
    "photo" -> stringResource(R.string.partner_tab_photo)
    "services" -> stringResource(R.string.partner_tab_services)
    "chats" -> stringResource(R.string.partner_tab_chats)
    "profile" -> stringResource(R.string.profile_title)
    "instructors" -> stringResource(R.string.admin_center_instructors_title)
    else -> key
}

private val PartnerTabToggleableOrder = listOf("explore", "feed", "courses", "trips", "photo", "services", "chats", "profile")
private val PartnerAllTabOrder = listOf("dashboard", "explore", "feed", "courses", "trips", "photo", "services", "chats", "profile")

private fun isTabHidden(k: String, m: Map<String, Any?>): Boolean {
    val s = m["bottomBarHiddenTabs"] as? List<*> ?: return false
    return s.mapNotNull { it?.toString()?.lowercase(Locale.ROOT) }.contains(k.lowercase(Locale.ROOT))
}

private fun setTabHiddenInMap(m: MutableMap<String, Any?>, k: String, hidden: Boolean) {
    if (k.equals("dashboard", true)) return
    val h = (m["bottomBarHiddenTabs"] as? List<*>)
        ?.mapNotNull { it?.toString()?.lowercase(Locale.ROOT) }
        ?.toMutableSet() ?: LinkedHashSet()
    h.remove("dashboard")
    if (hidden) h.add(k.lowercase(Locale.ROOT)) else h.remove(k.lowercase(Locale.ROOT))
    m["bottomBarHiddenTabs"] = ArrayList(h)
}

private fun fullBottomBarOrder(adm: Map<*, Any?>): List<String> {
    @Suppress("UNCHECKED_CAST")
    val m = adm as? Map<*, *>
    val baseOrder = (m?.get("bottomBarOrder") as? List<*>)
        ?.mapNotNull { it?.toString()?.trim()?.lowercase(Locale.ROOT) }
        ?.filter { it in PartnerAllTabOrder } ?: emptyList()
    val merged = mutableListOf<String>()
    for (k in baseOrder) {
        if (k !in merged) merged.add(k)
    }
    for (k in PartnerAllTabOrder) {
        if (k !in merged) {
            if (k == "chats") {
                val p = merged.indexOf("profile")
                if (p >= 0) merged.add(p, "chats") else merged.add(k)
            } else {
                merged.add(k)
            }
        }
    }
    return merged
}

private fun visibleTabOrder(adm: Map<*, Any?>): List<String> {
    @Suppress("UNCHECKED_CAST")
    val m = adm as? Map<*, *>
    val full = fullBottomBarOrder(adm)
    val hidden = (m?.get("bottomBarHiddenTabs") as? List<*>)
        ?.mapNotNull { it?.toString()?.lowercase(Locale.ROOT) }
        ?.toSet() ?: emptySet()
    return full.filter { it == "dashboard" || it !in hidden }
}

private fun applyBottomBarOrderFromVisible(adm: MutableMap<String, Any?>, reorderedVisible: List<String>) {
    val full = fullBottomBarOrder(adm)
    @Suppress("UNCHECKED_CAST")
    val m = adm as? Map<*, *>
    val hidden = (m?.get("bottomBarHiddenTabs") as? List<*>)
        ?.mapNotNull { it?.toString()?.lowercase(Locale.ROOT) }
        ?.toSet() ?: emptySet()
    val hiddenOrdered = full.filter { it in hidden }
    adm["bottomBarOrder"] = ArrayList(reorderedVisible + hiddenOrdered)
}

private fun iosBlockOrderKeys(adm: Map<*, Any?>): List<String> {
    @Suppress("UNCHECKED_CAST")
    val m = adm as? Map<*, *>
    val raw = (m?.get(AdminDashboardLayout.KEY_SECTION_ORDER) as? List<*>)
        ?.mapNotNull { it?.toString()?.trim()?.lowercase(Locale.ROOT) }
        ?: emptyList()
    var o = raw.filter { it in AdminDashboardLayout.IOS_BLOCK_KEYS }
    for (k in AdminDashboardLayout.IOS_BLOCK_KEYS) {
        if (k !in o) o = o + listOf(k)
    }
    if (o.isEmpty()) o = AdminDashboardLayout.IOS_BLOCK_KEYS
    return o
}

private fun patchSectionOrderIosFromQuickCal(quickCal: List<String>, m: MutableMap<String, Any?>, user: UserDto) {
    m[AdminDashboardLayout.KEY_SECTION_ORDER] = ArrayList(
        AdminDashboardLayout.mergeUnifiedSectionOrder(quickCal, AdminDashboardLayout.fromDiverProfile(user.diverProfile)),
    )
}

private fun quickActionOptions(adm: Map<*, Any?>): List<String> {
    val v = visibleTabOrder(adm)
    val o = v.toMutableList()
    if (o.contains("profile") && "instructors" !in o) o.add("instructors")
    return o
}

@Composable
private fun AdminPanelSectionLabel(t: String) {
    Text(
        t,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun IosGrouped(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = IosDesign.CardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) { Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(0.dp), content = { content() }) }
}

@Composable
private fun AdminPanelToggleRow(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
        )
    }
}
