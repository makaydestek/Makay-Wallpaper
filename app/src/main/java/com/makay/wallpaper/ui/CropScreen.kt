package com.makay.wallpaper.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.makay.wallpaper.core.WallpaperApplier
import com.makay.wallpaper.data.AlbumRepository
import com.makay.wallpaper.data.WallpaperSettingsRepository
import com.makay.wallpaper.util.LanguageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    albumId: String,
    imageUri: String,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { AlbumRepository.getInstance(context) }
    val settings = remember { WallpaperSettingsRepository.getInstance(context) }
    val existing = repo.getById(albumId)?.images?.find { it.uri == imageUri }

    var left by remember { mutableFloatStateOf(existing?.cropLeft ?: 0.1f) }
    var top by remember { mutableFloatStateOf(existing?.cropTop ?: 0.1f) }
    var right by remember { mutableFloatStateOf(existing?.cropRight ?: 0.9f) }
    var bottom by remember { mutableFloatStateOf(existing?.cropBottom ?: 0.9f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        LanguageManager.t("Kırp", "Crop", context),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    TvIconButton(onClick = onDone) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                LanguageManager.t(
                    "Kutuyu sürükleyerek kırpma alanını ayarlayın. “Ekran oranına kırp” hedef çözünürlük oranını uygular.",
                    "Drag to adjust crop. “Crop to screen ratio” matches your target resolution aspect.",
                    context
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val dx = drag.x / w
                                val dy = drag.y / h
                                left = (left + dx).coerceIn(0f, right - 0.05f)
                                right = (right + dx).coerceIn(left + 0.05f, 1f)
                                top = (top + dy).coerceIn(0f, bottom - 0.05f)
                                bottom = (bottom + dy).coerceIn(top + 0.05f, 1f)
                            }
                        }
                ) {
                    val l = left * size.width
                    val t = top * size.height
                    val r = right * size.width
                    val b = bottom * size.height
                    drawRect(
                        color = Color.Black.copy(alpha = 0.45f),
                        size = Size(size.width, size.height)
                    )
                    drawRect(
                        color = Color(0xFF4ECDC4),
                        topLeft = Offset(l, t),
                        size = Size((r - l).coerceAtLeast(1f), (b - t).coerceAtLeast(1f)),
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            TvActionButton(
                onClick = {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    runCatching {
                        context.contentResolver.openInputStream(android.net.Uri.parse(imageUri))
                            ?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    }
                    val fit = settings.displayFit.value
                    val (tw, th) = WallpaperApplier.resolveTargetSize(
                        context,
                        fit,
                        bounds.outWidth,
                        bounds.outHeight
                    )
                    val imgW = bounds.outWidth.coerceAtLeast(1)
                    val imgH = bounds.outHeight.coerceAtLeast(1)
                    val rect = WallpaperApplier.centeredCropForAspect(imgW, imgH, tw, th)
                    left = rect.left
                    top = rect.top
                    right = rect.right
                    bottom = rect.bottom
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                idle = MaterialTheme.colorScheme.secondary
            ) {
                Text(
                    LanguageManager.t("Ekran oranına kırp", "Crop to screen ratio", context)
                )
            }

            Spacer(Modifier.height(8.dp))
            TvActionButton(
                onClick = {
                    repo.updateCrop(albumId, imageUri, left, top, right, bottom)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                idle = MaterialTheme.colorScheme.primary
            ) {
                Text(LanguageManager.t("Kaydet", "Save", context), fontWeight = FontWeight.Bold)
            }
        }
    }
}
