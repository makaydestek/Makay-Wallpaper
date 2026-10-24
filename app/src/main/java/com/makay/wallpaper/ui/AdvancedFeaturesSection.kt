package com.makay.wallpaper.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makay.wallpaper.core.WallpaperController
import com.makay.wallpaper.data.AlbumRepository
import com.makay.wallpaper.data.WallpaperSettingsRepository
import com.makay.wallpaper.domain.model.Album
import com.makay.wallpaper.domain.model.ImageEffects
import com.makay.wallpaper.domain.model.TransitionStyle
import com.makay.wallpaper.util.BackupRestore
import com.makay.wallpaper.util.LanguageManager

@Composable
fun AdvancedFeaturesSection() {
    val context = LocalContext.current
    val settings = remember { WallpaperSettingsRepository.getInstance(context) }
    val albumsRepo = remember { AlbumRepository.getInstance(context) }
    val effects by settings.effects.collectAsState()
    val behavior by settings.behavior.collectAsState()
    val dayPart by settings.dayPart.collectAsState()
    val lockAlbumId by settings.lockAlbumId.collectAsState()
    val albumList by albumsRepo.albums.collectAsState()
    var backupMsg by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupMsg = if (BackupRestore.exportToUri(context, uri)) {
            LanguageManager.t("Yedek kaydedildi", "Backup saved", context)
        } else {
            LanguageManager.t("Yedek başarısız", "Backup failed", context)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupMsg = if (BackupRestore.importFromUri(context, uri)) {
            LanguageManager.t("Geri yükleme tamam", "Restore complete", context)
        } else {
            LanguageManager.t("Geri yükleme başarısız", "Restore failed", context)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            LanguageManager.t("Görsel efektler", "Image effects", context),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EffectSlider(
                    LanguageManager.t("Bulanıklık", "Blur", context),
                    effects.blurRadius,
                    0f..25f
                ) { settings.setEffects(effects.copy(blurRadius = it)) }
                EffectSlider(
                    LanguageManager.t("Parlaklık (− karart / + aydınlat)", "Brightness", context),
                    effects.brightness,
                    -0.5f..0.5f
                ) { settings.setEffects(effects.copy(brightness = it)) }
                EffectSlider(
                    LanguageManager.t("Vinyet", "Vignette", context),
                    effects.vignette,
                    0f..1f
                ) { settings.setEffects(effects.copy(vignette = it)) }
                AdvTriggerRow(
                    LanguageManager.t("Gri ton", "Grayscale", context),
                    effects.grayscale
                ) { settings.setEffects(effects.copy(grayscale = it)) }
                TvActionButton(
                    onClick = {
                        settings.setEffects(ImageEffects())
                        WallpaperController.applyCurrent(context)
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    idle = MaterialTheme.colorScheme.secondary
                ) {
                    Text(LanguageManager.t("Efektleri sıfırla + uygula", "Reset effects + apply", context))
                }
                TvActionButton(
                    onClick = { WallpaperController.applyCurrent(context) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    idle = MaterialTheme.colorScheme.primary
                ) {
                    Text(LanguageManager.t("Efektlerle yeniden uygula", "Re-apply with effects", context))
                }
            }
        }

        Text(
            LanguageManager.t("Ana / kilit albümü", "Home / lock albums", context),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    LanguageManager.t(
                        "Kilit ekranı için ayrı albüm (boş = ana ile aynı)",
                        "Separate lock album (empty = same as home)",
                        context
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilterChip(
                    selected = lockAlbumId == null,
                    onClick = { WallpaperController.setLockAlbum(context, null) },
                    label = { Text(LanguageManager.t("Aynı", "Same", context)) }
                )
                albumList.forEach { a ->
                    FilterChip(
                        selected = lockAlbumId == a.id,
                        onClick = { WallpaperController.setLockAlbum(context, a.id) },
                        label = { Text(a.name) }
                    )
                }
            }
        }

        Text(
            LanguageManager.t("Günün saatine göre albüm", "Time-of-day albums", context),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdvTriggerRow(
                    LanguageManager.t("Saat dilimi albümleri açık", "Time-of-day albums on", context),
                    dayPart.enabled
                ) { settings.setDayPart(dayPart.copy(enabled = it)) }
                if (dayPart.enabled) {
                    DayPartPicker(
                        LanguageManager.t("Sabah (06–12)", "Morning (06–12)", context),
                        dayPart.morningAlbumId,
                        albumList
                    ) { settings.setDayPart(dayPart.copy(morningAlbumId = it)) }
                    DayPartPicker(
                        LanguageManager.t("Öğle (12–18)", "Afternoon (12–18)", context),
                        dayPart.afternoonAlbumId,
                        albumList
                    ) { settings.setDayPart(dayPart.copy(afternoonAlbumId = it)) }
                    DayPartPicker(
                        LanguageManager.t("Akşam (18–22)", "Evening (18–22)", context),
                        dayPart.eveningAlbumId,
                        albumList
                    ) { settings.setDayPart(dayPart.copy(eveningAlbumId = it)) }
                    DayPartPicker(
                        LanguageManager.t("Gece (22–06)", "Night (22–06)", context),
                        dayPart.nightAlbumId,
                        albumList
                    ) { settings.setDayPart(dayPart.copy(nightAlbumId = it)) }
                }
            }
        }

        Text(
            LanguageManager.t("Davranış", "Behavior", context),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdvTriggerRow(
                    LanguageManager.t(
                        "Yatay modda değiştirmeyi durdur",
                        "Pause changes in landscape",
                        context
                    ),
                    behavior.pauseInLandscape
                ) { settings.setBehavior(behavior.copy(pauseInLandscape = it)) }
                Text(
                    LanguageManager.t("Geçiş (canlı WP)", "Transition (live WP)", context),
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = behavior.transition == TransitionStyle.NONE,
                        onClick = {
                            settings.setBehavior(behavior.copy(transition = TransitionStyle.NONE))
                        },
                        label = { Text(LanguageManager.t("Yok", "None", context)) }
                    )
                    FilterChip(
                        selected = behavior.transition == TransitionStyle.FADE,
                        onClick = {
                            settings.setBehavior(behavior.copy(transition = TransitionStyle.FADE))
                        },
                        label = { Text(LanguageManager.t("Fade", "Fade", context)) }
                    )
                }
            }
        }

        Text(
            LanguageManager.t("Yedekleme", "Backup", context),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    LanguageManager.t(
                        "Albümler ve ayarlar JSON olarak dışa/içe aktarılır.",
                        "Albums and settings export/import as JSON.",
                        context
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TvActionButton(
                    onClick = { exportLauncher.launch("MakayWallpaper-backup.json") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    idle = MaterialTheme.colorScheme.primary
                ) {
                    Text(LanguageManager.t("Yedek al", "Export backup", context))
                }
                TvActionButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    idle = MaterialTheme.colorScheme.secondary
                ) {
                    Text(LanguageManager.t("Yedekten geri yükle", "Restore backup", context))
                }
                backupMsg?.let { msg ->
                    Text(msg, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun EffectSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Slider(value = value, onValueChange = onChange, valueRange = range)
}

@Composable
private fun AdvTriggerRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    TvFocusRow(onClick = { onChange(!checked) }) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.tvSkipChildFocus()
        )
    }
}

@Composable
private fun DayPartPicker(
    label: String,
    selectedId: String?,
    albums: List<Album>,
    onSelect: (String?) -> Unit
) {
    val context = LocalContext.current
    Text(label, fontWeight = FontWeight.SemiBold)
    FilterChip(
        selected = selectedId == null,
        onClick = { onSelect(null) },
        label = { Text(LanguageManager.t("Yok", "None", context)) }
    )
    albums.forEach { a ->
        FilterChip(
            selected = selectedId == a.id,
            onClick = { onSelect(a.id) },
            label = { Text(a.name) }
        )
    }
}
