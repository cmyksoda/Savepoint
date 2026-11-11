package com.cmyksoda.savepoint

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PokemonPicker(catalog: PokemonCatalog, artwork: Artwork, onChoose: (PokemonChoice) -> Unit) {
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
            val index = catalog.index()
            all = index.choices.filter { index.games(it.slug).isNotEmpty() }
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
            AeroButton(onClick = { attempt++ }) { Text("Try again") }
        }
        if (all.isEmpty() && error == null) CircularProgressIndicator(Modifier.size(24.dp))
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            items(results, key = { it.slug }) { pokemon ->
                Glass(
                    Modifier.fillMaxWidth().feedbackClickable {
                        focus.clearFocus()
                        keyboard?.hide()
                        onChoose(pokemon)
                    }
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(44.dp).clearAndSetSemantics {},
                            contentAlignment = Alignment.Center,
                        ) {
                            pokemon.spriteUrl?.let { url ->
                                Art(
                                    url,
                                    artwork,
                                    pokemon.label,
                                    sprite = true,
                                    transparent = true,
                                    showPlaceholderTitle = false,
                                    coverBounds = CoverSize(44f, 44f),
                                    reserveHeight = false,
                                    preview = true,
                                )
                            }
                        }
                        Text(pokemon.label)
                    }
                }
            }
        }
    }
}
