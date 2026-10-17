package com.stille.messenger.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** One pager owns tab selection, swipe position and saved page state. */
@Composable
fun SwipeSections(
    items: List<BottomNavItem>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    layout: @Composable (navigation: @Composable () -> Unit, pages: @Composable () -> Unit) -> Unit,
    content: @Composable (String) -> Unit
) {
    val pager = rememberPagerState(initialPage = 1, pageCount = { items.size })
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    androidx.compose.runtime.LaunchedEffect(pager, focus) {
        androidx.compose.runtime.snapshotFlow { pager.isScrollInProgress }.collect { scrolling ->
            if (scrolling) focus.clearFocus()
        }
    }
    var navigationJob by remember { mutableStateOf<Job?>(null) }
    layout(
        {
            BottomBar(items, items[pager.currentPage].route, onItemSelected = { route ->
                val destination = items.indexOfFirst { it.route == route }
                if (destination >= 0 && enabled) {
                    focus.clearFocus()
                    navigationJob?.cancel()
                    navigationJob = scope.launch {
                        pager.animateScrollToPage(destination, animationSpec = tween(200, easing = FastOutSlowInEasing))
                    }
                }
            })
        },
        {
            HorizontalPager(
                state = pager,
                modifier = modifier.fillMaxSize(),
                userScrollEnabled = enabled,
                key = { items[it].route },
                beyondViewportPageCount = 0
            ) { page ->
                Box(Modifier.fillMaxSize().graphicsLayer {
                    val offset = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                    scaleX = 1f - 0.015f * offset
                    scaleY = scaleX
                    alpha = 1f - 0.08f * offset
                }) { content(items[page].route) }
            }
        }
    )
}

/** Zero blur at rest and at page edges; strongest midway through a transition. */
internal fun sectionBlurProgress(offset: Float): Float {
    val bounded = offset.coerceIn(0f, 1f)
    return 4f * bounded * (1f - bounded)
}
