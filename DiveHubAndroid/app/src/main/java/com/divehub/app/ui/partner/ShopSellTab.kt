package com.divehub.app.ui.partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.AuthRepository
import com.divehub.app.data.ShopSellRepository
import com.divehub.app.data.remote.dto.ShopOrderLocal
import com.divehub.app.data.remote.dto.ShopProductLocal
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.ui.trips.TripsListTabContent
import kotlinx.coroutines.launch
import java.util.Locale

private enum class ShopProductsFilter(val value: String) {
    ALL("all"),
    ACTIVE("active"),
    DRAFT("draft"),
    ARCHIVED("archived"),
}

private enum class ShopOrdersFilter(val value: String) {
    ALL("all"),
    NEW("new"),
    PAID("paid"),
    SHIPPED("shipped"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopSellTab(
    graph: AppGraph,
    innerNav: NavController,
) {
    val repo = remember { ShopSellRepository(graph) }
    val scope = rememberCoroutineScope()
    var segment by remember { mutableIntStateOf(0) }
    var shopId by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var products by remember { mutableStateOf<List<ShopProductLocal>>(emptyList()) }
    var productQ by remember { mutableStateOf("") }
    var productsFilter by remember { mutableStateOf(ShopProductsFilter.ALL) }
    var productEditor by remember { mutableStateOf<ShopProductLocal?>(null) }
    var showCreateProduct by remember { mutableStateOf(false) }

    var orders by remember { mutableStateOf<List<ShopOrderLocal>>(emptyList()) }
    var orderQ by remember { mutableStateOf("") }
    var ordersFilter by remember { mutableStateOf(ShopOrdersFilter.ALL) }
    var orderEditor by remember { mutableStateOf<ShopOrderLocal?>(null) }
    var showCreateOrder by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loadError = null
            runCatching {
                val user = AuthRepository(graph).cachedUser()
                val sid = user?.shopId?.trim().orEmpty()
                shopId = sid.ifBlank { null }
                if (sid.isBlank()) {
                    products = emptyList()
                    orders = emptyList()
                } else {
                    repo.syncFromRemoteOrCache(sid)
                    products = repo.loadProducts(sid).sortedByDescending { it.updatedAt }
                    orders = repo.loadOrders(sid).sortedByDescending { it.createdAt }
                }
            }.onFailure { e ->
                loadError = e.message ?: "Error"
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.shop_sell_local_ledger_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
        PrimaryTabRow(selectedTabIndex = segment) {
            Tab(
                selected = segment == 0,
                onClick = { segment = 0 },
                text = { Text(stringResource(R.string.shop_tab_products)) },
            )
            Tab(
                selected = segment == 1,
                onClick = { segment = 1 },
                text = { Text(stringResource(R.string.shop_tab_orders)) },
            )
            Tab(
                selected = segment == 2,
                onClick = { segment = 2 },
                text = { Text(stringResource(R.string.shop_tab_trips)) },
            )
        }
        when (segment) {
            0 -> ShopProductsPanel(
                products = products,
                query = productQ,
                onQueryChange = { productQ = it },
                filter = productsFilter,
                onFilterChange = { productsFilter = it },
                onRefresh = { refresh() },
                onCreate = { showCreateProduct = true },
                onEdit = { productEditor = it },
                onArchiveToggle = { p ->
                    val sid = shopId ?: return@ShopProductsPanel
                    scope.launch {
                        val next = if (p.status.equals("archived", ignoreCase = true)) {
                            p.copy(status = "active")
                        } else {
                            p.copy(status = "archived")
                        }
                        repo.upsertProduct(next)
                        products = repo.loadProducts(sid).sortedByDescending { it.updatedAt }
                    }
                },
                noShopLinked = shopId == null,
                loadError = loadError,
            )
            1 -> ShopOrdersPanel(
                orders = orders,
                query = orderQ,
                onQueryChange = { orderQ = it },
                filter = ordersFilter,
                onFilterChange = { ordersFilter = it },
                onRefresh = { refresh() },
                onCreate = { showCreateOrder = true },
                onEdit = { orderEditor = it },
                noShopLinked = shopId == null,
                loadError = loadError,
            )
            2 -> TripsListTabContent(
                graph = graph,
                innerNav = innerNav,
                showCreateFab = true,
                onCreateTrip = { innerNav.navigate(InnerRoutes.TripCreate) },
            )
        }
    }

    if (showCreateProduct) {
        ProductEditorSheet(
            title = stringResource(R.string.shop_products_create),
            initial = null,
            onDismiss = { showCreateProduct = false },
            onSave = { name, price, stock, status ->
                val sid = shopId ?: return@ProductEditorSheet
                scope.launch {
                    repo.createProduct(sid, name, price, stock, status)
                    products = repo.loadProducts(sid).sortedByDescending { it.updatedAt }
                    showCreateProduct = false
                }
            },
        )
    }

    productEditor?.let { selected ->
        ProductEditorSheet(
            title = stringResource(R.string.shop_products_edit),
            initial = selected,
            onDismiss = { productEditor = null },
            onSave = { name, price, stock, status ->
                val sid = shopId ?: return@ProductEditorSheet
                scope.launch {
                    repo.upsertProduct(selected.copy(name = name, price = price, stock = stock, status = status))
                    products = repo.loadProducts(sid).sortedByDescending { it.updatedAt }
                    productEditor = null
                }
            },
        )
    }

    if (showCreateOrder) {
        OrderEditorSheet(
            title = stringResource(R.string.shop_orders_create),
            initial = null,
            onDismiss = { showCreateOrder = false },
            onSave = { customer, itemCount, total, status ->
                val sid = shopId ?: return@OrderEditorSheet
                scope.launch {
                    repo.createOrder(sid, customer, itemCount, total, status)
                    orders = repo.loadOrders(sid).sortedByDescending { it.createdAt }
                    showCreateOrder = false
                }
            },
        )
    }

    orderEditor?.let { selected ->
        OrderEditorSheet(
            title = stringResource(R.string.shop_orders_edit),
            initial = selected,
            onDismiss = { orderEditor = null },
            onSave = { customer, itemCount, total, status ->
                val sid = shopId ?: return@OrderEditorSheet
                scope.launch {
                    repo.upsertOrder(
                        selected.copy(
                            customerName = customer,
                            itemCount = itemCount,
                            total = total,
                            status = status,
                        ),
                    )
                    orders = repo.loadOrders(sid).sortedByDescending { it.createdAt }
                    orderEditor = null
                }
            },
        )
    }
}

@Composable
private fun ShopProductsPanel(
    products: List<ShopProductLocal>,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: ShopProductsFilter,
    onFilterChange: (ShopProductsFilter) -> Unit,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (ShopProductLocal) -> Unit,
    onArchiveToggle: (ShopProductLocal) -> Unit,
    noShopLinked: Boolean,
    loadError: String?,
) {
    val q = query.trim().lowercase()
    val filtered = products.filter { p ->
        val passFilter = when (filter) {
            ShopProductsFilter.ALL -> true
            else -> p.status.equals(filter.value, ignoreCase = true)
        }
        val passQuery = q.isBlank() || p.name.lowercase().contains(q)
        passFilter && passQuery
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(14.dp),
    ) {
        HeaderRow(
            query = query,
            queryLabel = stringResource(R.string.shop_products_search),
            onQueryChange = onQueryChange,
            onRefresh = onRefresh,
            onCreate = onCreate,
        )
        ChipRow {
            ShopProductsFilter.entries.forEach { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { onFilterChange(item) },
                    label = { Text(productsFilterLabel(item)) },
                )
            }
        }
        loadError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (noShopLinked) {
            Text(stringResource(R.string.shop_no_shop_id), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        when {
            products.isEmpty() -> {
                Text(stringResource(R.string.shop_products_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            filtered.isEmpty() -> {
                Text(stringResource(R.string.shop_products_empty_filtered), color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.id }) { p ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text(productsFilterLabel(p.status), color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            stringResource(
                                R.string.shop_products_line_price_stock,
                                "$" + String.format(Locale.US, "%.2f", p.price),
                                p.stock,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onEdit(p) }) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Text(stringResource(R.string.common_edit), modifier = Modifier.padding(start = 6.dp))
                            }
                            OutlinedButton(onClick = { onArchiveToggle(p) }) {
                                val archived = p.status.equals("archived", ignoreCase = true)
                                Icon(if (archived) Icons.Default.Unarchive else Icons.Default.Archive, contentDescription = null)
                                Text(
                                    if (archived) stringResource(R.string.shop_products_unarchive) else stringResource(R.string.shop_products_archive),
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopOrdersPanel(
    orders: List<ShopOrderLocal>,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: ShopOrdersFilter,
    onFilterChange: (ShopOrdersFilter) -> Unit,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (ShopOrderLocal) -> Unit,
    noShopLinked: Boolean,
    loadError: String?,
) {
    val q = query.trim().lowercase()
    val filtered = orders.filter { o ->
        val passFilter = when (filter) {
            ShopOrdersFilter.ALL -> true
            else -> o.status.equals(filter.value, ignoreCase = true)
        }
        val passQuery = q.isBlank() ||
            o.customerName.lowercase().contains(q) ||
            o.id.lowercase().contains(q)
        passFilter && passQuery
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(14.dp),
    ) {
        HeaderRow(
            query = query,
            queryLabel = stringResource(R.string.shop_orders_search),
            onQueryChange = onQueryChange,
            onRefresh = onRefresh,
            onCreate = onCreate,
        )
        ChipRow {
            ShopOrdersFilter.entries.forEach { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { onFilterChange(item) },
                    label = { Text(ordersFilterLabel(item)) },
                )
            }
        }
        loadError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (noShopLinked) {
            Text(stringResource(R.string.shop_no_shop_id), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        when {
            orders.isEmpty() -> {
                Text(stringResource(R.string.shop_orders_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            filtered.isEmpty() -> {
                Text(stringResource(R.string.shop_orders_empty_filtered), color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.id }) { o ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(o.customerName, fontWeight = FontWeight.SemiBold)
                            Text(ordersFilterLabel(o.status), color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            stringResource(
                                R.string.shop_orders_line_items_total,
                                o.itemCount,
                                "$" + String.format(Locale.US, "%.2f", o.total),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.shop_orders_line_id, o.id),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = { onEdit(o) }) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Text(stringResource(R.string.common_edit), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(
    query: String,
    queryLabel: String,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(queryLabel) },
        singleLine = true,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh))
        }
        IconButton(onClick = onCreate) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.shop_create_item))
        }
    }
}

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun productsFilterLabel(filter: ShopProductsFilter): String = when (filter) {
    ShopProductsFilter.ALL -> stringResource(R.string.shop_filter_all)
    ShopProductsFilter.ACTIVE -> stringResource(R.string.shop_filter_active)
    ShopProductsFilter.DRAFT -> stringResource(R.string.shop_filter_draft)
    ShopProductsFilter.ARCHIVED -> stringResource(R.string.shop_filter_archived)
}

@Composable
private fun productsFilterLabel(status: String): String = when {
    status.equals("active", ignoreCase = true) -> stringResource(R.string.shop_filter_active)
    status.equals("draft", ignoreCase = true) -> stringResource(R.string.shop_filter_draft)
    status.equals("archived", ignoreCase = true) -> stringResource(R.string.shop_filter_archived)
    else -> status
}

@Composable
private fun ordersFilterLabel(filter: ShopOrdersFilter): String = when (filter) {
    ShopOrdersFilter.ALL -> stringResource(R.string.shop_filter_all)
    ShopOrdersFilter.NEW -> stringResource(R.string.shop_order_status_new)
    ShopOrdersFilter.PAID -> stringResource(R.string.shop_order_status_paid)
    ShopOrdersFilter.SHIPPED -> stringResource(R.string.shop_order_status_shipped)
    ShopOrdersFilter.COMPLETED -> stringResource(R.string.shop_order_status_completed)
    ShopOrdersFilter.CANCELLED -> stringResource(R.string.shop_order_status_cancelled)
}

@Composable
private fun ordersFilterLabel(status: String): String = when {
    status.equals("new", ignoreCase = true) -> stringResource(R.string.shop_order_status_new)
    status.equals("paid", ignoreCase = true) -> stringResource(R.string.shop_order_status_paid)
    status.equals("shipped", ignoreCase = true) -> stringResource(R.string.shop_order_status_shipped)
    status.equals("completed", ignoreCase = true) -> stringResource(R.string.shop_order_status_completed)
    status.equals("cancelled", ignoreCase = true) -> stringResource(R.string.shop_order_status_cancelled)
    else -> status
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductEditorSheet(
    title: String,
    initial: ShopProductLocal?,
    onDismiss: () -> Unit,
    onSave: (name: String, price: Double, stock: Int, status: String) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var price by remember(initial?.id) { mutableStateOf(initial?.price?.toString().orEmpty()) }
    var stock by remember(initial?.id) { mutableStateOf(initial?.stock?.toString().orEmpty()) }
    var status by remember(initial?.id) { mutableStateOf(initial?.status ?: "active") }
    val priceValue = price.toDoubleOrNull()
    val stockValue = stock.toIntOrNull()
    val valid = name.trim().isNotBlank() && priceValue != null && priceValue >= 0.0 && stockValue != null && stockValue >= 0

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.shop_products_field_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = price,
                onValueChange = { price = it },
                label = { Text(stringResource(R.string.shop_products_field_price)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = stock,
                onValueChange = { stock = it },
                label = { Text(stringResource(R.string.shop_products_field_stock)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShopProductsFilter.entries.filter { it != ShopProductsFilter.ALL }.forEach { option ->
                    FilterChip(
                        selected = status.equals(option.value, ignoreCase = true),
                        onClick = { status = option.value },
                        label = { Text(productsFilterLabel(option)) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = { onSave(name.trim(), priceValue ?: 0.0, stockValue ?: 0, status) },
                    enabled = valid,
                ) {
                    Text(stringResource(R.string.common_save))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderEditorSheet(
    title: String,
    initial: ShopOrderLocal?,
    onDismiss: () -> Unit,
    onSave: (customer: String, itemCount: Int, total: Double, status: String) -> Unit,
) {
    var customer by remember(initial?.id) { mutableStateOf(initial?.customerName.orEmpty()) }
    var itemCount by remember(initial?.id) { mutableStateOf(initial?.itemCount?.toString().orEmpty()) }
    var total by remember(initial?.id) { mutableStateOf(initial?.total?.toString().orEmpty()) }
    var status by remember(initial?.id) { mutableStateOf(initial?.status ?: "new") }
    val itemCountValue = itemCount.toIntOrNull()
    val totalValue = total.toDoubleOrNull()
    val valid = customer.trim().isNotBlank() && itemCountValue != null && itemCountValue > 0 && totalValue != null && totalValue >= 0.0

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = customer,
                onValueChange = { customer = it },
                label = { Text(stringResource(R.string.shop_orders_field_customer)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = itemCount,
                onValueChange = { itemCount = it },
                label = { Text(stringResource(R.string.shop_orders_field_items)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = total,
                onValueChange = { total = it },
                label = { Text(stringResource(R.string.shop_orders_field_total)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShopOrdersFilter.entries.filter { it != ShopOrdersFilter.ALL }.forEach { option ->
                    FilterChip(
                        selected = status.equals(option.value, ignoreCase = true),
                        onClick = { status = option.value },
                        label = { Text(ordersFilterLabel(option)) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = { onSave(customer.trim(), itemCountValue ?: 0, totalValue ?: 0.0, status) },
                    enabled = valid,
                ) {
                    Text(stringResource(R.string.common_save))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
