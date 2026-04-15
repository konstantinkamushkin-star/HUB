package com.divehub.app.ui.partner

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.ui.trips.TripsListTabContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopSellTab(
    graph: AppGraph,
    innerNav: NavController,
) {
    var segment by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
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
            0 -> ShopPlaceholderPanel(
                title = stringResource(R.string.shop_products_title),
                body = stringResource(R.string.shop_products_placeholder),
            )
            1 -> ShopPlaceholderPanel(
                title = stringResource(R.string.shop_orders_title),
                body = stringResource(R.string.shop_orders_placeholder),
            )
            2 -> TripsListTabContent(
                graph = graph,
                innerNav = innerNav,
                showCreateFab = false,
                onCreateTrip = { },
            )
        }
    }
}

@Composable
private fun ShopPlaceholderPanel(title: String, body: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
