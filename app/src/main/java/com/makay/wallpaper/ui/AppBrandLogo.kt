package com.makay.wallpaper.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makay.wallpaper.R

/**
 * Makay Wallpaper marka logosu (Cleaner ikonundan ayrı; mavi + manzara çerçevesi).
 */
@Composable
fun AppBrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    corner: Dp = 20.dp
) {
    Image(
        painter = painterResource(R.drawable.ic_app_logo),
        contentDescription = "Makay Wallpaper",
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner)),
        contentScale = ContentScale.Fit
    )
}
