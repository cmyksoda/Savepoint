package com.cmyksoda.savepoint

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal val bottomTabRoutes = listOf("home", "backlog", "paused", "log")

@Composable
internal fun ScreenContentHost(
    route: String,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit,
) {
    AnimatedContent(
        targetState = route,
        contentKey = { if (it in bottomTabRoutes) "tabs" else it },
        transitionSpec = {
            if (initialState in bottomTabRoutes && targetState in bottomTabRoutes)
                EnterTransition.None.togetherWith(ExitTransition.None).using(null)
            else fadeIn(tween(110)).togetherWith(fadeOut(tween(0))).using(null)
        },
        modifier = modifier.clipToBounds(),
        label = "screen transition",
    ) { screen ->
        if (screen in bottomTabRoutes) BottomTabContent(screen, content) else content(screen)
    }
}

private class TabSlides(initial: String) {
    val visible = mutableStateListOf(initial)
    val offsets = bottomTabRoutes.associateWith { Animatable(if (it == initial) 0f else 1f) }
    private var selected = initial

    suspend fun select(route: String) {
        if (route == selected) return
        val direction =
            if (bottomTabRoutes.indexOf(route) > bottomTabRoutes.indexOf(selected)) 1f else -1f
        if (route !in visible) offsets.getValue(route).snapTo(direction)
        val incoming = offsets.getValue(route).value
        selected = route
        visible.remove(route)
        visible.add(route)
        coroutineScope {
            visible.toList().forEach { page ->
                launch {
                    val offset = offsets.getValue(page)
                    val destination =
                        when {
                            page == route -> 0f
                            offset.value <= incoming -> -1f
                            else -> 1f
                        }
                    offset.animateTo(destination, tween(230, easing = FastOutSlowInEasing))
                }
            }
        }
        visible.clear()
        visible.add(route)
    }
}

@Composable
internal fun BottomTabContent(route: String, content: @Composable (String) -> Unit) {
    val slides = remember { TabSlides(route) }
    LaunchedEffect(route) { slides.select(route) }
    Layout(
        content = {
            bottomTabRoutes.forEach { page ->
                key(page) { Box(Modifier.fillMaxSize()) { content(page) } }
            }
        },
        modifier = Modifier.fillMaxSize().clipToBounds(),
    ) { measurables, constraints ->
        // Measure the tab viewports before the first switch; inactive pages are never placed.
        val pages = measurables.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            slides.visible.forEach { page ->
                pages[bottomTabRoutes.indexOf(page)].placeWithLayer(0, 0) {
                    translationX = slides.offsets.getValue(page).value * constraints.maxWidth
                }
            }
        }
    }
}
