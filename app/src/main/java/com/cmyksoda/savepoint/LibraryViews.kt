package com.cmyksoda.savepoint

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun LibraryBrowser(
    games: List<Game>,
    grid: Boolean,
    emptyMessage: String,
    controls: @Composable () -> Unit,
    card: @Composable (Game, Boolean, Modifier) -> Unit,
) {
    val listState = rememberLazyListState()
    val gridState = rememberLazyStaggeredGridState()
    val modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)
    if (grid)
        LazyVerticalStaggeredGrid(
            StaggeredGridCells.Adaptive(160.dp),
            modifier,
            state = gridState,
            contentPadding = PaddingValues(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp,
        ) {
            item(key = "controls", span = StaggeredGridItemSpan.FullLine) { controls() }
            if (games.isEmpty())
                item(span = StaggeredGridItemSpan.FullLine) {
                    Text(emptyMessage, Modifier.padding(vertical = 20.dp))
                }
            staggeredItems(games, key = { it.id }) { game ->
                card(game, true, Modifier.animateItem())
            }
        }
    else
        LazyColumn(
            modifier,
            state = listState,
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "controls") { controls() }
            if (games.isEmpty()) item { Text(emptyMessage, Modifier.padding(vertical = 20.dp)) }
            items(games, key = { it.id }) { game -> card(game, false, Modifier.animateItem()) }
        }
}

@Composable
fun LibraryGameCard(
    game: Game,
    artwork: Artwork,
    grid: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
    showSystem: Boolean = true,
    extras: @Composable ColumnScope.() -> Unit = {},
) {
    val identity: @Composable ColumnScope.() -> Unit = {
        Text(
            game.title,
            fontWeight = FontWeight.SemiBold,
            maxLines = if (grid) 3 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
        if (showSystem) Text(game.system, style = MaterialTheme.typography.bodySmall)
        extras()
    }
    val image: @Composable () -> Unit = {
        Art(
            if (game.shelf == Shelf.HUNTS) game.sprite else game.cover,
            artwork,
            game.title,
            Modifier.clip(RoundedCornerShape(8.dp)),
            sprite = game.shelf == Shelf.HUNTS,
            transparent = game.shelf == Shelf.HUNTS,
            showPlaceholderTitle = false,
            system = game.system,
            coverBounds = if (grid) CoverSize(140f, 188f) else CoverSize(54f, 72f),
            reserveHeight = false,
        )
    }
    Glass(modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        if (grid)
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { image() }
                identity()
            }
        else
            Row(
                Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                image()
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    content = identity,
                )
                AeroIcon(
                    AeroSymbol.NEXT,
                    Modifier.size(16.dp),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}
