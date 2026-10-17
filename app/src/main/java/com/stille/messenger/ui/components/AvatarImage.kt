package com.stille.messenger.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

/** Bounded in-memory decode; initials remain visible on missing/invalid images. */
@Composable
fun AvatarImage(name: String, bytes: ByteArray?, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val context = LocalContext.current
    val request = remember(bytes, context) {
        ImageRequest.Builder(context).data(bytes).size(288)
            .diskCachePolicy(CachePolicy.DISABLED).memoryCachePolicy(CachePolicy.DISABLED)
            .crossfade(false).build()
    }
    Box(modifier.size(size).clip(CircleShape)) {
        InitialAvatar(name, size = size)
        if (bytes != null) AsyncImage(
            model = request, contentDescription = null,
            modifier = Modifier.size(size), contentScale = ContentScale.Crop
        )
    }
}
