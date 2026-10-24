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
 * Makay Wallpaper marka logosu.
 * Buyuk boyut: MAKAY yazili tam ikon (ekosistem).
 * Kucuk boyut: geometrik M + duvar kagidi rozeti.
 */
@Composable
fun AppBrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    corner: Dp = 20.dp
) {
    val resId = if (size >= 56.dp) {
        R.drawable.ic_launcher_fg
    } else {
        R.drawable.ic_app_logo
    }
    Image(
        painter = painterResource(resId),
        contentDescription = "Makay Wallpaper",
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner)),
        contentScale = ContentScale.Crop
    )
}
