package com.stille.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stille.coreapi.Entity
import com.stille.messenger.ui.theme.BlueAccent
import com.stille.messenger.ui.theme.StilleColors

// ─── Glass surface ─────────────────────────────────────────────────────────

/**
 * Glass-style surface: translucent gradient background and a light border.
 * The translucency approximates the glassmorphism effect on all API levels;
 * the wallpaper behind it stays visible through the surface.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    backgroundColor: Color = if (StilleColors.isDark) Color(0x29FFFFFF) else Color(0x30FFFFFF),
    borderColor: Color = if (StilleColors.isDark) Color(0x33FFFFFF) else Color(0x40FFFFFF),
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .background(
                brush = if (StilleColors.isDark) {
                    Brush.verticalGradient(listOf(Color(0x18FFFFFF), Color(0x0DFFFFFF)))
                } else {
                    Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x28FFFFFF)))
                },
                shape = shape
            )
            .border(1.dp, borderColor, shape)
    ) {
        content()
    }
}

// ─── Chat bubble ───────────────────────────────────────────────────────────

@Composable
fun ChatBubble(
    isMine: Boolean,
    delivered: Boolean,
    body: String,
    modifier: Modifier = Modifier,
    timestamp: String = "",
    entities: List<Entity> = emptyList()
) {
    val bubbleColor = if (isMine) BlueAccent
    else if (StilleColors.isDark) Color(0xFF1E1E1E) else Color(0xFFE5E5E5)
    val textColor = if (isMine || StilleColors.isDark) Color.White else Color.Black

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
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                val annotated = buildAnnotatedString {
                    append(body)
                    for (e in entities) {
                        when (e.kind) {
                            1 -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), e.offset, e.offset + e.length)
                            2 -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), e.offset, e.offset + e.length)
                            3 -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), e.offset, e.offset + e.length)
                            4 -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), e.offset, e.offset + e.length)
                            5 -> addStyle(SpanStyle(background = Color(0x40000000)), e.offset, e.offset + e.length)
                            6, 7 -> addStyle(SpanStyle(fontFamily = FontFamily.Monospace), e.offset, e.offset + e.length)
                        }
                    }
                }

                Text(
                    text = annotated,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                    softWrap = true
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (timestamp.isNotEmpty()) {
                        Text(
                            text = timestamp,
                            color = textColor.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (isMine) {
                        Text(
                            text = if (delivered) "✓✓" else "✓",
                            color = textColor.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
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
        cornerRadius = 0.dp,
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
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color(0x10FFFFFF)
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
    searchHint: String = "",
    onSearchChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 12.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = if (StilleColors.isDark) Color.White else Color(0xFF121212)
            )
            if (searchHint.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        onSearchChange(it)
                    },
                    placeholder = {
                        Text(
                            searchHint,
                            color = if (StilleColors.isDark) Color(0x80FFFFFF) else Color(0x80121212)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BlueAccent,
                        unfocusedBorderColor = Color(0x40FFFFFF),
                        focusedTextColor = if (StilleColors.isDark) Color.White else Color.Black,
                        unfocusedTextColor = if (StilleColors.isDark) Color.White else Color.Black
                    )
                )
            }
        }
    }
}

// ─── Three-dot overflow menu ───────────────────────────────────────────────

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
                tint = if (StilleColors.isDark) Color.White else Color(0xFF121212)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = if (StilleColors.isDark) Color(0xFF2C2C2C) else Color(0xFFF0F0F0)
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            item.title,
                            color = if (StilleColors.isDark) Color.White else Color.Black
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
