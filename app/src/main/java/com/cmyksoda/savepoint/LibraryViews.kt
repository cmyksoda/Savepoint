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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
        if (showSystem)
            Text(
                game.system,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        extras()
    }
    val image: @Composable () -> Unit = {
        GameThumbnail(
            game,
            artwork,
            bounds = if (grid) CoverSize(140f, 188f) else CoverSize(54f, 72f),
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

@Composable
fun GameThumbnail(game: Game, artwork: Artwork, bounds: CoverSize = CoverSize(54f, 72f)) {
    val hunt = game.shelf == Shelf.HUNTS
    // A fixed slot keeps titles beside thumbnails on one edge whatever the cover's shape.
    Box(
        Modifier.width(bounds.width.dp).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Art(
            if (hunt) game.sprite else game.cover,
            artwork,
            if (hunt) displayPokemonName(game.pokemon) else game.title,
            sprite = hunt,
            transparent = hunt,
            showPlaceholderTitle = false,
            system = game.system,
            coverBounds = bounds,
            reserveHeight = false,
        )
    }
}

@Composable
fun ArrangeCard(
    modifier: Modifier,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onMove: (Int) -> Unit,
    extra: (@Composable () -> Unit)? = null,
    identity: @Composable RowScope.() -> Unit,
) {
    Glass(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onOpen)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                identity()
                AeroIcon(
                    AeroSymbol.NEXT,
                    Modifier.size(14.dp),
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
                )
            }
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).fillMaxHeight(), propagateMinConstraints = true) {
                    AeroButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                        AeroIcon(AeroSymbol.UP, Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("move up", maxLines = 1)
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), propagateMinConstraints = true) {
                    AeroButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                        AeroIcon(AeroSymbol.DOWN, Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("move down", maxLines = 1)
                    }
                }
                extra?.invoke()
            }
        }
    }
}

@Composable
fun RowScope.GameArrangeIdentity(game: Game, artwork: Artwork, detail: String? = null) {
    GameThumbnail(game, artwork)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            game.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            game.system,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        detail?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
