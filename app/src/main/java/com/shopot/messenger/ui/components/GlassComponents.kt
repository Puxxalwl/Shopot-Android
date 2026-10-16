package com.shopot.messenger.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopot.messenger.ui.theme.ShopotColors
import com.shopot.messenger.ui.theme.BlueAccent

// ─── Glass surface with blur ────────────────────────────────────────────────────

/**
 * Glass-style surface: translucent background, white/gray border, blurred backdrop.
 * Uses RenderEffect on API 31+ (native blur). On API 26–30 falls back to a
 * translucent tint with no blur (still looks glass-like).
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 4.dp,
    backgroundColor: Color = if (ShopotColors.isDark) Color(0x29FFFFFF) else Color(0x30FFFFFF),
    borderColor: Color = if (ShopotColors.isDark) Color(0x33FFFFFF) else Color(0x40FFFFFF),
    enableBlur: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .background(
                brush = if (ShopotColors.isDark) {
                    Brush.verticalGradient(listOf(Color(0x18FFFFFF), Color(0x0DFFFFFF)))
                } else {
                    Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x28FFFFFF)))
                },
                shape = RoundedCornerLayoutShape(cornerRadius)
            )
            .border(1.dp, borderColor, RoundedCornerLayoutShape(cornerRadius))
            .graphicsLayer {
                if (enableBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = android.graphics.RenderEffect.createBlurEffect(
                        25f * density.density / 160f,
                        25f * density.density / 160f,
                        android.graphics.RenderEffect.SHADER
                    )
                }
            }
    ) {
        content()
    }
}

private fun RoundedCornerLayoutShape(cornerRadius: Dp) =
    RoundedCornerShape(
        topStart = cornerRadius,
        topEnd = cornerRadius,
        bottomStart = cornerRadius,
        bottomEnd = cornerRadius
    )

// ─── Chat bubble ───────────────────────────────────────────────────────────────

@Composable
fun ChatBubble(
    isMine: Boolean,
    delivered: Boolean,
    body: String,
    modifier: Modifier = Modifier,
    timestamp: String = "",
    entities: List<com.shopot.coreapi.Entity> = emptyList()
) {
    val bubbleColor = if (isMine) BlueAccent else if (ShopotColors.isDark) Color(0xFF1E1E1E) else Color(0xFFE5E5E5)
    val textColor = if (isMine || ShopotColors.isDark) Color.White else Color.Black

    val bubbleShape = remember(isMine) {
        RoundedCornerShape(
            topStart = if (isMine) 18.dp else 4.dp,
            topEnd = if (isMine) 4.dp else 18.dp,
            bottomStart = 18.dp,
            bottomEnd = 18.dp
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (isMine) 72.dp else 8.dp,
                end = if (isMine) 8.dp else 72.dp,
                top = 2.dp,
                bottom = 2.dp
            )
    ) {
        Box(
            modifier = Modifier
                .background(bubbleColor, shape = bubbleShape)
                .border(0.5.dp, bubbleColor.copy(alpha = 0.3f), shape = bubbleShape)
                .padding(12.dp, 8.dp)
        ) {
            Column {
                val annotated = buildAnnotatedString {
                    append(body)
                    for (e in entities) {
                        when (e.kind) {
                            1 -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), e.offset, e.offset + e.length)
                            2 -> addStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), e.offset, e.offset + e.length)
                            3 -> addStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), e.offset, e.offset + e.length)
                            4 -> addStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough), e.offset, e.offset + e.length)
                            5 -> addStyle(SpanStyle(background = Color(0x40000000)), e.offset, e.offset + e.length)
                            6, 7 -> addStyle(SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), e.offset, e.offset + e.length)
                        }
                    }
                }

                androidx.compose.material3.Text(
                    text = annotated,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                    softWrap = true
                )

                if (timestamp.isNotEmpty()) {
                    Text(
                        text = timestamp,
                        color = textColor.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }

                if (isMine) {
                    val marks = if (delivered) "✓✓" else "✓"
                    Text(
                        text = marks,
                        color = textColor.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp)
                    )
                }
            }
        }
    }
}

data class BottomNavItem(
    val title: String,
    val icon: ImageVector,
    val route: String
)

// ─── Bottom navigation bar ─────────────────────────────────────────────────

@Composable
fun BottomBar(
    items: List<BottomNavItem>,
    currentRoute: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth().height(64.dp),
        cornerRadius = Dp.Value(0f),
        backgroundColor = Color(0x18FFFFFF)
    ) {
        NavigationBar(containerColor = Color.Transparent) {
            items.forEach { item ->
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = if (currentRoute == item.route) BlueAccent else Color(0x80FFFFFF)
                        )
                    },
                    label = {
                        Text(
                            text = item.title,
                            color = if (currentRoute == item.route) BlueAccent else Color(0x80FFFFFF)
                        )
                    },
                    selected = currentRoute == item.route,
                    onClick = { onItemSelected(item.route) },
                    colors = androidx.compose.material3.NavigationBarItemDefaults.navigationBarItemColors(
                        containerColor = Color.Transparent,
                        selectedIconContainerColor = Color(0x10FFFFFF),
                        unselectedIconContainerColor = Color.Transparent
                    )
                )
            }
        }
    }
}

// ─── Top app bar with search ───────────────────────────────────────────────

@Composable
fun TopAppBarWithSearch(
    title: String,
    searchHint: String = "Поиск",
    onSearchChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = Dp.Value(12f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = if (ShopotColors.isDark) Color.White else Color(0xFF121212)
            )
            if (searchHint.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        onSearchChange(it)
                    },
                    placeholder = { Text(searchHint, color = if (ShopotColors.isDark) Color(0x80FFFFFF) else Color(0x80121212)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = BlueAccent,
                        unfocusedBorderColor = Color(0x40FFFFFF),
                        textColor = if (ShopotColors.isDark) Color.White else Color.Black
                    )
                )
            }
        }
    }
}

// ─── Three-dot overflow menu ────────────────────────────────────────────────

data class MenuItem(val title: String, val icon: ImageVector? = null)

@Composable
fun ThreeDotMenu(
    items: List<MenuItem>,
    onItemSelected: (MenuItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Меню",
                tint = if (ShopotColors.isDark) Color.White else Color(0xFF121212)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = if (ShopotColors.isDark) Color(0xFF2C2C2C) else Color(0xFFF0F0F0)
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            item.title,
                            color = if (ShopotColors.isDark) Color.White else Color.Black
                        )
                    },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}
