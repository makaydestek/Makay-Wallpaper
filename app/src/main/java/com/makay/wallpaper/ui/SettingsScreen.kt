package com.makay.wallpaper.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makay.wallpaper.BuildConfig
import com.makay.wallpaper.data.ThemeRepository
import com.makay.wallpaper.data.WallpaperSettingsRepository
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.makay.wallpaper.core.WallpaperApplier
import com.makay.wallpaper.core.WallpaperController
import com.makay.wallpaper.domain.model.DisplayFitSettings
import com.makay.wallpaper.domain.model.FitMode
import com.makay.wallpaper.domain.model.GestureAction
import com.makay.wallpaper.domain.model.ResolutionSource
import com.makay.wallpaper.domain.model.WallpaperTarget
import com.makay.wallpaper.ui.theme.ThemeMode
import com.makay.wallpaper.update.AppUpdateViewModel
import com.makay.wallpaper.update.findActivity
import com.makay.wallpaper.util.LanguageManager
import com.makay.wallpaper.util.PlatformManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val themeRepository = remember { ThemeRepository.getInstance(context) }
    val settings = remember { WallpaperSettingsRepository.getInstance(context) }
    val currentTheme by themeRepository.themeMode.collectAsState()
    val currentDynamic by themeRepository.dynamicColor.collectAsState()
    val triggers by settings.triggers.collectAsState()
    val gestures by settings.gestures.collectAsState()
    val target by settings.target.collectAsState()
    val displayFit by settings.displayFit.collectAsState()
    val currentLang by LanguageManager.lang.collectAsState()
    LanguageManager.init(context)
    val deviceSize = remember { WallpaperApplier.deviceScreenSize(context) }

    val activity = context.findActivity() as? FragmentActivity
    val updateVm: AppUpdateViewModel? = if (activity != null) {
        viewModel(viewModelStoreOwner = activity, factory = AppUpdateViewModel.Factory(app))
    } else null
    val updateState = updateVm?.state?.collectAsState()?.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        LanguageManager.t("Ayarlar", "Settings", context),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    TvIconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.tvRequestInitialFocus()
                    ) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                LanguageManager.t("Görünüm", "Appearance", context),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        LanguageManager.t("Tema Modu", "Theme mode", context),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ThemeMode.entries.forEach { mode ->
                        TvFocusRow(onClick = { themeRepository.setThemeMode(mode) }) {
                            RadioButton(
                                selected = currentTheme == mode,
                                onClick = null,
                                modifier = Modifier.tvSkipChildFocus(),
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(Modifier.padding(horizontal = 6.dp))
                            Text(mode.displayName)
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                TvFocusRow(
                    onClick = { themeRepository.setDynamicColor(!currentDynamic) },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            LanguageManager.t("Dinamik Renk", "Dynamic color", context),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            LanguageManager.t(
                                "Android 12+ cihazlarda sistem renkleri",
                                "System colors on Android 12+",
                                context
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = currentDynamic,
                        onCheckedChange = { themeRepository.setDynamicColor(it) },
                        modifier = Modifier.tvSkipChildFocus()
                    )
                }
            }

            Text(
                LanguageManager.t("Cihaz ve dil", "Device and language", context),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    PlatformManager.Platform.entries.forEach { platform ->
                        val selected = PlatformManager.getPlatform(context) == platform
                        TvFocusRow(onClick = {
                            PlatformManager.setPlatform(context, platform)
                        }) {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                modifier = Modifier.tvSkipChildFocus()
                            )
                            Spacer(Modifier.padding(horizontal = 6.dp))
                            Text(
                                if (platform == PlatformManager.Platform.TV)
                                    LanguageManager.t("TV", "TV", context)
                                else
                                    LanguageManager.t("Mobil", "Mobile", context)
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    LanguageManager.Lang.entries.forEach { lang ->
                        TvFocusRow(onClick = { LanguageManager.set(context, lang) }) {
                            RadioButton(
                                selected = currentLang == lang,
                                onClick = null,
                                modifier = Modifier.tvSkipChildFocus()
                            )
                            Spacer(Modifier.padding(horizontal = 6.dp))
                            Text(lang.displayName)
                        }
                    }
                }
            }

            Text(
                LanguageManager.t("Tetikleyiciler", "Triggers", context),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(8.dp)) {
                    TriggerRow(
                        LanguageManager.t("Ekran açılınca", "On screen on", context),
                        triggers.onScreenOn
                    ) { settings.setTriggers(triggers.copy(onScreenOn = it)) }
                    TriggerRow(
                        LanguageManager.t("Ekran kapanınca", "On screen off", context),
                        triggers.onScreenOff
                    ) { settings.setTriggers(triggers.copy(onScreenOff = it)) }
                    TriggerRow(
                        LanguageManager.t("Kilidi açınca", "On unlock", context),
                        triggers.onUnlock
                    ) { settings.setTriggers(triggers.copy(onUnlock = it)) }
                    TriggerRow(
                        LanguageManager.t(
                            "Ana ekran görününce (canlı WP)",
                            "When home shown (live WP)",
                            context
                        ),
                        triggers.onHomeShown
                    ) { settings.setTriggers(triggers.copy(onHomeShown = it)) }
                    TriggerRow(
                        LanguageManager.t(
                            "Ana ekran gizlenince / uygulama açılınca (canlı WP)",
                            "When home hidden / app opened (live WP)",
                            context
                        ),
                        triggers.onHomeHidden
                    ) { settings.setTriggers(triggers.copy(onHomeHidden = it)) }
                }
            }

            Text(
                LanguageManager.t("Dokunma jestleri", "Touch gestures", context),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var statusTick by remember { mutableStateOf(0) }
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) statusTick++
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }
                    val liveActive = remember(statusTick, gestures) {
                        com.makay.wallpaper.util.LiveWallpaperHelper.isOurLiveWallpaperActive(context)
                    }
                    val a11yOn = remember(statusTick) {
                        com.makay.wallpaper.service.DoubleTapAccessibilityService.isEnabled(context)
                    }
                    val setupOk = liveActive && a11yOn && gestures.enabled
                    Text(
                        LanguageManager.t(
                            "Ana ekranda çift dokunuşla sonraki fotoğraf için " +
                                "aşağıdaki adımları sırayla tamamlayın (Xiaomi / HyperOS):",
                            "To change wallpaper with a home double-tap, complete these steps " +
                                "(Xiaomi / HyperOS):",
                            context
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        LanguageManager.t(
                            "• Telefon: Ayarlar → Kilit ekranı → «Çift dokunun» kapalı olsun\n" +
                                "• 1) Canlı duvar kağıdı → «Ana ekran» seçin\n" +
                                "• 2) Jest griyse: Kısıtlanmış ayara izin verin\n" +
                                "• 3) «Makay Wallpaper jestleri»ni açın\n" +
                                "• 4) Gestures açık + Çift dokunuş = NEXT",
                            "• Phone: Settings → Lock screen → turn Double tap off\n" +
                                "• 1) Set live wallpaper → Home\n" +
                                "• 2) If gestures are greyed: Allow restricted settings\n" +
                                "• 3) Enable “Makay Wallpaper gestures”\n" +
                                "• 4) Gestures on + Double tap = NEXT",
                            context
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        LanguageManager.t(
                            "Durum: ${com.makay.wallpaper.util.LiveWallpaperHelper.statusLabel(context)}" +
                                if (setupOk) " · hazır" else " · eksik adım var",
                            "Status: ${com.makay.wallpaper.util.LiveWallpaperHelper.statusLabel(context)}" +
                                if (setupOk) " · ready" else " · steps missing",
                            context
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (setupOk) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    TvActionButton(
                        onClick = {
                            com.makay.wallpaper.util.LiveWallpaperHelper.openLiveWallpaperSetup(context)
                            statusTick++
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        idle = MaterialTheme.colorScheme.secondary
                    ) {
                        Text(
                            LanguageManager.t(
                                "1) Canlı duvar kağıdını ayarla",
                                "1) Set live wallpaper",
                                context
                            )
                        )
                    }
                    TvActionButton(
                        onClick = {
                            com.makay.wallpaper.util.LiveWallpaperHelper
                                .openAppDetailsForRestrictedSettings(context)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        idle = MaterialTheme.colorScheme.errorContainer,
                        idleContent = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Text(
                            LanguageManager.t(
                                "2) Kısıtlanmış ayara izin ver",
                                "2) Allow restricted settings",
                                context
                            )
                        )
                    }
                    Text(
                        LanguageManager.t(
                            "«Makay Wallpaper jestleri» gri / «Kısıtlanmış ayar tarafından " +
                                "kontrol ediliyor» yazıyorsa: bu butona basın → sağ üst ⋮ → " +
                                "«Kısıtlanmış ayarlara izin ver» → açın. Sonra 3. adıma geçin.",
                            "If “Makay Wallpaper gestures” is greyed / “Controlled by a " +
                                "restricted setting”: tap this → top-right ⋮ → Allow restricted " +
                                "settings → On. Then continue with step 3.",
                            context
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TvActionButton(
                        onClick = {
                            com.makay.wallpaper.service.DoubleTapAccessibilityService
                                .openAccessibilitySettings(context)
                            statusTick++
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        idle = MaterialTheme.colorScheme.tertiary
                    ) {
                        Text(
                            LanguageManager.t(
                                "3) Jest erişilebilirliğini aç (MIUI)",
                                "3) Enable gesture accessibility (MIUI)",
                                context
                            )
                        )
                    }
                    Text(
                        LanguageManager.t(
                            "Erişilebilirlik → İndirilen uygulamalar → Makay Wallpaper jestleri → " +
                                "özelliği açın. Durum «erişilebilirlik açık» olmalı.",
                            "Accessibility → Downloaded apps → Makay Wallpaper gestures → turn on. " +
                                "Status should show accessibility on.",
                            context
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TvActionButton(
                        onClick = { statusTick++ },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        idle = MaterialTheme.colorScheme.surfaceVariant,
                        idleContent = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(LanguageManager.t("Durumu yenile", "Refresh status", context))
                    }
                    TriggerRow(
                        LanguageManager.t("Gestures açık", "Gestures enabled", context),
                        gestures.enabled
                    ) { enabled ->
                        settings.setGestures(gestures.copy(enabled = enabled))
                        if (enabled && !liveActive) {
                            com.makay.wallpaper.util.LiveWallpaperHelper.openLiveWallpaperSetup(context)
                        }
                        statusTick++
                    }
                    Text(LanguageManager.t("Çift dokunuş", "Double tap", context))
                    GestureChips(gestures.doubleTapAction) {
                        settings.setGestures(gestures.copy(doubleTapAction = it))
                    }
                    Text(LanguageManager.t("Üç dokunuş", "Triple tap", context))
                    GestureChips(gestures.tripleTapAction) {
                        settings.setGestures(gestures.copy(tripleTapAction = it))
                    }
                }
            }

            Text(
                LanguageManager.t("Ekrana sığdırma", "Fit to screen", context),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        LanguageManager.t(
                            "Sosyal medya / internet görselleri telefon oranına uymayabilir. Buradan tam ekran oturtma şeklini seçin.",
                            "Social / web images often don't match phone aspect. Choose how to fill the screen.",
                            context
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    FitHowToGuideCard(context)
                    HorizontalDivider()
                    Text(
                        LanguageManager.t("Sığdırma modu", "Fit mode", context),
                        fontWeight = FontWeight.SemiBold
                    )
                    FitMode.entries.forEach { mode ->
                        TvFocusRow(onClick = {
                            settings.setDisplayFit(displayFit.copy(fitMode = mode))
                        }) {
                            RadioButton(
                                selected = displayFit.fitMode == mode,
                                onClick = null,
                                modifier = Modifier.tvSkipChildFocus()
                            )
                            Spacer(Modifier.padding(horizontal = 6.dp))
                            Column {
                                Text(
                                    when (mode) {
                                        FitMode.FILL -> LanguageManager.t(
                                            "Doldur (önerilen)",
                                            "Fill (recommended)",
                                            context
                                        )
                                        FitMode.FIT -> LanguageManager.t(
                                            "Sığdır (şerit olabilir)",
                                            "Fit (may letterbox)",
                                            context
                                        )
                                        FitMode.STRETCH -> LanguageManager.t(
                                            "Esnet (oran bozulabilir)",
                                            "Stretch (may distort)",
                                            context
                                        )
                                    }
                                )
                                Text(
                                    when (mode) {
                                        FitMode.FILL -> LanguageManager.t(
                                            "Ekranı tamamen kaplar; fazla kenarlar kırpılır",
                                            "Covers the screen; edges may be cropped",
                                            context
                                        )
                                        FitMode.FIT -> LanguageManager.t(
                                            "Tüm görsel görünür; kenarlarda şerit olabilir",
                                            "Whole image visible; bars may appear",
                                            context
                                        )
                                        FitMode.STRETCH -> LanguageManager.t(
                                            "Görseli zorla ekran boyutuna çeker",
                                            "Forces image to screen size",
                                            context
                                        )
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDivider()
                    FitPreviewCard()

                    HorizontalDivider()
                    Text(
                        LanguageManager.t("Hedef çözünürlük", "Target resolution", context),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        LanguageManager.t(
                            "Cihaz: ${deviceSize.first}×${deviceSize.second}",
                            "Device: ${deviceSize.first}×${deviceSize.second}",
                            context
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ResolutionSource.entries.forEach { src ->
                        TvFocusRow(onClick = {
                            settings.setDisplayFit(displayFit.copy(resolutionSource = src))
                        }) {
                            RadioButton(
                                selected = displayFit.resolutionSource == src,
                                onClick = null,
                                modifier = Modifier.tvSkipChildFocus()
                            )
                            Spacer(Modifier.padding(horizontal = 6.dp))
                            Text(
                                when (src) {
                                    ResolutionSource.DEVICE -> LanguageManager.t(
                                        "Cihaz çözünürlüğü",
                                        "Device resolution",
                                        context
                                    )
                                    ResolutionSource.CUSTOM -> LanguageManager.t(
                                        "Özel (Custom)",
                                        "Custom",
                                        context
                                    )
                                }
                            )
                        }
                    }

                    if (displayFit.resolutionSource == ResolutionSource.CUSTOM) {
                        CustomResolutionFields(
                            fit = displayFit,
                            onChange = { settings.setDisplayFit(it) }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = displayFit.customWidth == 1220 &&
                                    displayFit.customHeight == 2712,
                                onClick = {
                                    settings.setDisplayFit(
                                        displayFit.copy(customWidth = 1220, customHeight = 2712)
                                    )
                                },
                                label = { Text("1220×2712") }
                            )
                            FilterChip(
                                selected = false,
                                onClick = {
                                    settings.setDisplayFit(
                                        displayFit.copy(
                                            customWidth = deviceSize.first,
                                            customHeight = deviceSize.second
                                        )
                                    )
                                },
                                label = {
                                    Text(
                                        LanguageManager.t("Cihazdan al", "From device", context)
                                    )
                                }
                            )
                        }
                        Text(
                            LanguageManager.t(
                                "Yatay: ${displayFit.customHeight}×${displayFit.customWidth} (otomatik)",
                                "Landscape: ${displayFit.customHeight}×${displayFit.customWidth} (auto)",
                                context
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TriggerRow(
                        LanguageManager.t(
                            "Yatay görselde W↔H değiştir",
                            "Swap W↔H for landscape images",
                            context
                        ),
                        displayFit.autoRotateDimsForLandscape
                    ) {
                        settings.setDisplayFit(
                            displayFit.copy(autoRotateDimsForLandscape = it)
                        )
                    }
                }
            }

            Text(
                LanguageManager.t("Hedef", "Target", context),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        LanguageManager.t(
                            "Yalnız «Ana ekran» seçiliyse kilit değişmemeli. " +
                                "Bazı telefonlarda (MIUI) bunun için aşağıdaki kilit albümünü ayrı seçin.",
                            "With Home only, lock should stay. On some phones (MIUI) also set a separate lock album below.",
                            context
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    WallpaperTarget.entries.forEach { t ->
                        TvFocusRow(onClick = {
                            settings.setTarget(t)
                            WallpaperController.applyCurrent(context)
                        }) {
                            RadioButton(
                                selected = target == t,
                                onClick = null,
                                modifier = Modifier.tvSkipChildFocus()
                            )
                            Spacer(Modifier.padding(horizontal = 6.dp))
                            Text(
                                when (t) {
                                    WallpaperTarget.HOME -> LanguageManager.t("Ana ekran", "Home", context)
                                    WallpaperTarget.LOCK -> LanguageManager.t("Kilit ekranı", "Lock", context)
                                    WallpaperTarget.BOTH -> LanguageManager.t("İkisi", "Both", context)
                                }
                            )
                        }
                    }
                }
            }

            HomeLockAlbumSection()

            Text(
                LanguageManager.t("Pil ve arka plan", "Battery & background", context),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var batteryTick by remember { mutableStateOf(0) }
                    val batteryLifecycle = LocalLifecycleOwner.current
                    DisposableEffect(batteryLifecycle) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) batteryTick++
                        }
                        batteryLifecycle.lifecycle.addObserver(observer)
                        onDispose { batteryLifecycle.lifecycle.removeObserver(observer) }
                    }
                    val batteryOk = remember(batteryTick) {
                        com.makay.wallpaper.util.BatteryOptimizationHelper
                            .isIgnoringBatteryOptimizations(context)
                    }
                    Text(
                        LanguageManager.t(
                            "Zamanlayıcı ve klasör izleme için pil kısıtlarını gevşetin. " +
                                "Xiaomi / Huawei / Oppo’da «otomatik başlat» da gerekir.",
                            "Relax battery limits for the timer and folder watch. " +
                                "On Xiaomi / Huawei / Oppo also allow autostart.",
                            context
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (batteryOk) {
                            LanguageManager.t(
                                "Durum: pil optimizasyonu kapalı (iyi)",
                                "Status: battery optimization off (good)",
                                context
                            )
                        } else {
                            LanguageManager.t(
                                "Durum: pil optimizasyonu açık — zamanlayıcı gecikebilir",
                                "Status: battery optimization on — timer may delay",
                                context
                            )
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (batteryOk) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    TvActionButton(
                        onClick = {
                            com.makay.wallpaper.util.BatteryOptimizationHelper
                                .requestIgnoreBatteryOptimizations(context)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        idle = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            LanguageManager.t(
                                "Pil kısıtını kaldır",
                                "Ignore battery optimizations",
                                context
                            )
                        )
                    }
                    TvActionButton(
                        onClick = {
                            com.makay.wallpaper.util.BatteryOptimizationHelper
                                .openOemAutostartSettings(context)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        idle = MaterialTheme.colorScheme.secondary
                    ) {
                        Text(
                            LanguageManager.t(
                                "Otomatik başlat / OEM ayarı",
                                "Autostart / OEM settings",
                                context
                            )
                        )
                    }
                }
            }

            val makayApps = remember {
                com.makay.wallpaper.util.MakayEcosystemHelper.installedApps(context)
            }
            if (makayApps.isNotEmpty()) {
                Text(
                    LanguageManager.t("Makay uygulamaları", "Makay apps", context),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            LanguageManager.t(
                                "Bu cihazda yüklü Makay uygulamaları:",
                                "Makay apps installed on this device:",
                                context
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        makayApps.forEach { app ->
                            TvActionButton(
                                onClick = {
                                    com.makay.wallpaper.util.MakayEcosystemHelper
                                        .openApp(context, app.packageName)
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                idle = MaterialTheme.colorScheme.surfaceVariant,
                                idleContent = MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Text(
                                    LanguageManager.t(app.labelTr, app.labelEn, context)
                                )
                            }
                        }
                    }
                }
            }

            AdvancedFeaturesSection()

            TvActionButton(
                onClick = { updateVm?.checkManual() },
                enabled = updateVm != null &&
                    updateState?.checking != true &&
                    updateState?.downloading != true,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                idle = androidx.compose.ui.graphics.Color(0xFF1565C0)
            ) {
                Text(
                    when {
                        updateState?.checking == true -> LanguageManager.t(
                            "Kontrol ediliyor…",
                            "Checking…",
                            context
                        )
                        updateState?.downloading == true -> LanguageManager.t(
                            "İndiriliyor…",
                            "Downloading…",
                            context
                        )
                        else -> LanguageManager.t(
                            "Güncelleme kontrol et",
                            "Check for updates",
                            context
                        )
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
            val updateMsg = updateState?.statusMessage ?: updateState?.error
            if (!updateMsg.isNullOrBlank()) {
                Text(
                    updateMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (updateState?.error != null)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "ℹ️ " + LanguageManager.t("Hakkında", "About", context),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppBrandLogo(size = 72.dp, corner = 16.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Makay Wallpaper",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = LanguageManager.t(
                                "Sürüm: ${BuildConfig.VERSION_NAME}",
                                "Version: ${BuildConfig.VERSION_NAME}",
                                context
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = LanguageManager.t("Geliştirici", "Developer", context),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = LanguageManager.t(
                            "Bu uygulamanın tasarımı ve geliştirmesi Murat AKAY tarafından yapılmıştır.",
                            "This app was designed and developed by Murat AKAY.",
                            context
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = LanguageManager.t(
                            "© 2026 Tüm hakları saklıdır. Sideload / yerel dağıtım için hazırlanmıştır.",
                            "© 2026 All rights reserved. Prepared for sideload / local distribution.",
                            context
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TriggerRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
private fun GestureChips(selected: GestureAction, onSelect: (GestureAction) -> Unit) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        GestureAction.entries.forEach { action ->
            FilterChip(
                selected = selected == action,
                onClick = { onSelect(action) },
                label = { Text(action.name, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun CustomResolutionFields(
    fit: DisplayFitSettings,
    onChange: (DisplayFitSettings) -> Unit
) {
    val context = LocalContext.current
    var wText by remember(fit.customWidth) { mutableStateOf(fit.customWidth.toString()) }
    var hText by remember(fit.customHeight) { mutableStateOf(fit.customHeight.toString()) }
    OutlinedTextField(
        value = wText,
        onValueChange = {
            wText = it.filter { c -> c.isDigit() }.take(5)
            val w = wText.toIntOrNull()?.coerceIn(240, 7680) ?: return@OutlinedTextField
            onChange(fit.copy(customWidth = w))
        },
        label = {
            Text(LanguageManager.t("Dikey genişlik (px)", "Portrait width (px)", context))
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = hText,
        onValueChange = {
            hText = it.filter { c -> c.isDigit() }.take(5)
            val h = hText.toIntOrNull()?.coerceIn(240, 7680) ?: return@OutlinedTextField
            onChange(fit.copy(customHeight = h))
        },
        label = {
            Text(LanguageManager.t("Dikey yükseklik (px)", "Portrait height (px)", context))
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
