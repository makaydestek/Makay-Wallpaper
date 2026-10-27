package com.makay.wallpaper.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.makay.wallpaper.core.WallpaperController
import com.makay.wallpaper.data.WallpaperSettingsRepository
import com.makay.wallpaper.domain.model.FitMode
import com.makay.wallpaper.util.LanguageManager

/**
 * Seçili sığdırma modunun hafif önizlemesi (mevcut duvar kağıdı görseli).
 * Varsayılan kapalı — Ayarlar’da yer kazanmak için göster/gizle.
 */
@Composable
fun FitPreviewCard(initiallyExpanded: Boolean = false) {
    val context = LocalContext.current
    val settings = remember { WallpaperSettingsRepository.getInstance(context) }
    val fit by settings.displayFit.collectAsState()
    val albumId by settings.activeAlbumId.collectAsState()
    val index by settings.activeIndex.collectAsState()
    val image = remember(albumId, index, fit.fitMode) {
        WallpaperController.currentImage(context)
    }
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                LanguageManager.t("Önizleme", "Preview", context),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                if (expanded) "▾" else "▸",
                color = MaterialTheme.colorScheme.primary
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(148.dp)
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(fit.letterboxColor)),
                    contentAlignment = Alignment.Center
                ) {
                    if (image != null) {
                        AsyncImage(
                            model = image.uri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = when (fit.fitMode) {
                                FitMode.FILL -> ContentScale.Crop
                                FitMode.FIT -> ContentScale.Fit
                                FitMode.STRETCH -> ContentScale.FillBounds
                            }
                        )
                    } else {
                        Text(
                            LanguageManager.t("Aktif görsel yok", "No active image", context),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                Text(
                    LanguageManager.t(
                        "Önizleme yaklaşık bir göstergedir. «Şimdi uygula» ile ekrana basılır.",
                        "Preview is approximate. Use Apply now to set on screen.",
                        context
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TvActionButton(
                    onClick = { WallpaperController.applyCurrent(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    idle = MaterialTheme.colorScheme.tertiary
                ) {
                    Text(LanguageManager.t("Şimdi uygula", "Apply now", context))
                }
            }
        }
    }
}
