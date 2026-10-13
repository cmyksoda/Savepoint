package com.cmyksoda.savepoint

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PokemonPicker(catalog: PokemonCatalog, onChoose: (PokemonChoice) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var all by remember { mutableStateOf<List<PokemonChoice>>(emptyList()) }
    var results by remember { mutableStateOf<List<PokemonChoice>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(catalog, attempt) {
        error = null
        try {
            all = catalog.all()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = "Couldn’t load Pokémon. Try again when connected."
        }
    }
    LaunchedEffect(all, query) {
        results = withContext(Dispatchers.Default) { searchPokemon(all, query) }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AeroTextField(
            query,
            { query = it },
            label = "Pokémon or form",
            singleLine = true,
            autoFocus = true,
            onSubmit = {
                focus.clearFocus()
                keyboard?.hide()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Choose a Pokémon first, then its game.", style = MaterialTheme.typography.bodySmall)
        error?.let {
            Text(it)
            AeroButton(onClick = { attempt++ }) { Text("try again") }
        }
        if (all.isEmpty() && error == null) CircularProgressIndicator(Modifier.size(24.dp))
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            items(results, key = { it.slug }) { pokemon ->
                Glass(
                    Modifier.fillMaxWidth().clickable {
                        focus.clearFocus()
                        keyboard?.hide()
                        onChoose(pokemon)
                    }
                ) {
                    Text(pokemon.label, Modifier.padding(16.dp))
                }
            }
        }
    }
}
