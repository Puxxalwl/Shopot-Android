package com.stille.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stille.coreapi.Entity
import com.stille.messenger.ui.theme.StilleColors

/** Lightweight glass-inspired surface: translucent fill + edge highlight, no backdrop capture. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val dark = StilleColors.isDark
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    // 40% translucent glass fill; no offscreen blur, capture or elevated layer.
    val base = backgroundColor ?: MaterialTheme.colorScheme.surface.copy(alpha = 0.40f)
    val fill = remember(base) { Brush.linearGradient(listOf(base, base.copy(alpha = base.alpha * 0.85f))) }
    val edge = remember(dark, borderColor) {
        Brush.linearGradient(listOf(
            borderColor ?: Color.White.copy(alpha = if (dark) 0.24f else 0.85f),
            borderColor ?: Color.White.copy(alpha = if (dark) 0.05f else 0.25f),
            borderColor ?: Color.White.copy(alpha = if (dark) 0.13f else 0.55f)
        ))
    }
    Box(
        modifier.clip(shape)
            .background(fill)
            .border(1.dp, edge, shape),
        content = content
    )
}

@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title, modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
fun InitialAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val palettes = listOf(
        listOf(Color(0xFF548AFF), Color(0xFF8068E9)),
        listOf(Color(0xFF3FADBC), Color(0xFF4B7CD8)),
        listOf(Color(0xFFC28DD0), Color(0xFF8B6CC1))
    )
    val palette = palettes[(name.hashCode() and Int.MAX_VALUE) % palettes.size]
    Box(
        modifier.size(size).clip(CircleShape).background(Brush.linearGradient(palette)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(2).uppercase(),
            color = Color.White,
            fontSize = (size.value * 0.32f).sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun QuietEmptyState(icon: ImageVector, title: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun ChatBubble(
    isMine: Boolean,
    delivered: Boolean,
    body: String,
    modifier: Modifier = Modifier,
    timestamp: String = "",
    entities: List<Entity> = emptyList()
) {
    val background = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val foreground = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val annotated = remember(body, entities) {
        buildAnnotatedString {
            append(body)
            entities.forEach { entity ->
                // Do not let malformed display ranges crash composition. Wire validation is unchanged.
                if (entity.offset >= 0 && entity.length > 0 && entity.offset <= body.length && entity.length <= body.length - entity.offset) {
                    val style = when (entity.kind) {
                        1 -> SpanStyle(fontWeight = FontWeight.Bold)
                        2 -> SpanStyle(fontStyle = FontStyle.Italic)
                        3 -> SpanStyle(textDecoration = TextDecoration.Underline)
                        4 -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                        5 -> SpanStyle(background = Color(0x20000000))
                        6, 7 -> SpanStyle(fontFamily = FontFamily.Monospace)
                        else -> null
                    }
                    style?.let { addStyle(it, entity.offset, entity.offset + entity.length) }
                }
            }
        }
    }
    Box(
        modifier.fillMaxWidth().padding(start = if (isMine) 56.dp else 16.dp, end = if (isMine) 16.dp else 56.dp, top = 3.dp, bottom = 3.dp),
        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            Modifier.widthIn(max = 340.dp).background(background, RoundedCornerShape(20.dp, 20.dp, if (isMine) 6.dp else 20.dp, if (isMine) 20.dp else 6.dp)).padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(annotated, color = foreground, style = MaterialTheme.typography.bodyLarge)
            if (timestamp.isNotEmpty() || isMine) {
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(timestamp.takeIf { it.isNotEmpty() }, if (isMine) { if (delivered) "✓✓" else "✓" } else null).joinToString("  "),
                    color = foreground.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

data class BottomNavItem(val title: String, val icon: ImageVector, val route: String)

@Composable
fun BottomBar(items: List<BottomNavItem>, currentRoute: String, onItemSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp).fillMaxWidth(), cornerRadius = 30.dp) {
        Column {
            Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 6.dp, vertical = 5.dp)) {
                items.forEach { item ->
                    val selected = item.route == currentRoute
                    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    val highlight by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent, tween(140), label = "tab-highlight")
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(25.dp)).background(highlight).selectable(selected, role = Role.Tab, onClick = { if (!selected) onItemSelected(item.route) }).heightIn(min = 60.dp).padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(item.icon, null, Modifier.size(24.dp), tint = color)
                        Spacer(Modifier.height(5.dp))
                        Text(item.title, color = color, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun TopAppBarWithSearch(title: String, searchHint: String = "", onSearchChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    // Restore both displayed query and the screen filter after recreation.
    LaunchedEffect(query) { onSearchChange(query) }
    Column(modifier.fillMaxWidth()) {
        if (title.isNotBlank()) ScreenTitle(title)
        if (searchHint.isNotBlank()) {
            BasicTextField(
                value = query,
                onValueChange = { query = it; onSearchChange(it) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                decorationBox = { field ->
                    Row(Modifier.heightIn(min = 48.dp).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(9.dp))
                        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                            if (query.isEmpty()) Text(searchHint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                            field()
                        }
                        if (query.isNotEmpty()) IconButton(onClick = { query = ""; onSearchChange("") }) {
                            Icon(Icons.Default.Close, if (StilleColors.language == "en") "Clear search" else "Очистить поиск", Modifier.size(20.dp))
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun SegmentedChoice(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val animatedIndex = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "segmented-indicator"
    )
    BoxWithConstraints(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .selectableGroup().padding(3.dp)
    ) {
        val segmentWidth = maxWidth / options.size.coerceAtLeast(1)
        Box(
            Modifier.offset {
                androidx.compose.ui.unit.IntOffset((segmentWidth.toPx() * animatedIndex.value).toInt(), 0)
            }.width(segmentWidth).heightIn(min = 48.dp).clip(RoundedCornerShape(19.dp))
                .background(MaterialTheme.colorScheme.surface)
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEach { (value, label) ->
                val active = value == selected
                Box(
                    Modifier.weight(1f).selectable(active, role = Role.RadioButton, onClick = { if (!active) onSelect(value) })
                        .heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
