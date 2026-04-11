package com.divehub.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.ui.navigation.InnerRoutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchRoute(graph: AppGraph, innerNav: NavController) {
    val vm: GlobalSearchViewModel = viewModel(factory = GlobalSearchViewModel.factory(graph))
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                label = { Text(stringResource(R.string.search_query_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { vm.search() },
                enabled = !state.loading,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.social_search))
            }
            when {
                state.loading -> BoxCenter { CircularProgressIndicator() }
                state.error != null -> Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                !state.hasSearched -> Text(
                    stringResource(R.string.search_intro_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                state.query.trim().length < 2 -> Text(
                    stringResource(R.string.search_min_chars),
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.users.isNotEmpty()) {
                        item {
                            Text(stringResource(R.string.search_section_users), style = MaterialTheme.typography.titleSmall)
                        }
                        items(state.users, key = { it.id }) { u ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { innerNav.navigate(InnerRoutes.userProfile(u.id)) },
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(u.displayName(), style = MaterialTheme.typography.titleMedium)
                                    Text(u.email, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if (state.sites.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.search_section_places), style = MaterialTheme.typography.titleSmall)
                        }
                        items(state.sites, key = { it.id }) { s ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(s.name, style = MaterialTheme.typography.titleMedium)
                                    val sub = listOfNotNull(s.region, s.country).joinToString(", ")
                                    if (sub.isNotBlank()) {
                                        Text(sub, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    if (state.hasSearched && !state.loading && state.users.isEmpty() && state.sites.isEmpty() && state.query.trim().length >= 2) {
                        item {
                            Text(stringResource(R.string.search_no_results), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxCenter(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}
