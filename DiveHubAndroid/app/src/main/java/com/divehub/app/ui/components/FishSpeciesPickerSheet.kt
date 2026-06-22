package com.divehub.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.divehub.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FishSpeciesPickerSheet(
    selected: List<String>,
    onSelectedChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val allSpecies = remember {
        listOf(
            "Clownfish", "Angelfish", "Butterflyfish", "Parrotfish", "Triggerfish",
            "Surgeonfish", "Wrasse", "Grouper", "Snapper", "Barracuda",
            "Shark", "Ray", "Turtle", "Moray Eel", "Lionfish",
            "Pufferfish", "Seahorse", "Octopus", "Squid", "Cuttlefish",
            "Lobster", "Crab", "Shrimp", "Nudibranch", "Sea Star",
            "Sea Urchin", "Jellyfish", "Manta Ray", "Whale Shark", "Dolphin",
            "Tuna", "Mackerel", "Jackfish", "Trevally", "Emperor",
            "Sweetlips", "Goatfish", "Squirrelfish", "Cardinalfish", "Damselfish",
        )
    }
    val filtered = remember(q, allSpecies) {
        if (q.isBlank()) allSpecies
        else allSpecies.filter { it.contains(q.trim(), ignoreCase = true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.logbook_select_fish_species), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                label = { Text(stringResource(R.string.logbook_search_fish_species)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.height(320.dp)) {
                items(filtered) { species ->
                    val checked = selected.contains(species)
                    ListItem(
                        headlineContent = { Text(species) },
                        trailingContent = {
                            if (checked) Text("✓", color = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (checked) onSelectedChange(selected.filterNot { it == species })
                            else onSelectedChange(selected + species)
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
        }
    }
}
