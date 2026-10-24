package com.makay.wallpaper.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.makay.wallpaper.domain.model.BehaviorSettings
import com.makay.wallpaper.domain.model.ChangeTriggers
import com.makay.wallpaper.domain.model.DayPartAlbums
import com.makay.wallpaper.domain.model.DisplayFitSettings
import com.makay.wallpaper.domain.model.FitMode
import com.makay.wallpaper.domain.model.GestureSettings
import com.makay.wallpaper.domain.model.ImageEffects
import com.makay.wallpaper.domain.model.TimerSettings
import com.makay.wallpaper.domain.model.WallpaperTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WallpaperSettingsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _timer = MutableStateFlow(loadTimer())
    val timer: StateFlow<TimerSettings> = _timer.asStateFlow()

    private val _triggers = MutableStateFlow(loadTriggers())
    val triggers: StateFlow<ChangeTriggers> = _triggers.asStateFlow()

    private val _gestures = MutableStateFlow(loadGestures())
    val gestures: StateFlow<GestureSettings> = _gestures.asStateFlow()

    private val _activeAlbumId = MutableStateFlow(prefs.getString(KEY_ALBUM, null))
    val activeAlbumId: StateFlow<String?> = _activeAlbumId.asStateFlow()

    private val _activeIndex = MutableStateFlow(prefs.getInt(KEY_INDEX, 0))
    val activeIndex: StateFlow<Int> = _activeIndex.asStateFlow()

    /** null = ana ekran albümü ile aynı */
    private val _lockAlbumId = MutableStateFlow(prefs.getString(KEY_LOCK_ALBUM, null))
    val lockAlbumId: StateFlow<String?> = _lockAlbumId.asStateFlow()

    private val _lockIndex = MutableStateFlow(prefs.getInt(KEY_LOCK_INDEX, 0))
    val lockIndex: StateFlow<Int> = _lockIndex.asStateFlow()

    private val _random = MutableStateFlow(prefs.getBoolean(KEY_RANDOM, false))
    val random: StateFlow<Boolean> = _random.asStateFlow()

    private val _target = MutableStateFlow(loadTarget())
    val target: StateFlow<WallpaperTarget> = _target.asStateFlow()

    private val _displayFit = MutableStateFlow(loadDisplayFit())
    val displayFit: StateFlow<DisplayFitSettings> = _displayFit.asStateFlow()

    private val _effects = MutableStateFlow(loadEffects())
    val effects: StateFlow<ImageEffects> = _effects.asStateFlow()

    private val _behavior = MutableStateFlow(loadBehavior())
    val behavior: StateFlow<BehaviorSettings> = _behavior.asStateFlow()

    private val _dayPart = MutableStateFlow(loadDayPart())
    val dayPart: StateFlow<DayPartAlbums> = _dayPart.asStateFlow()

    private val _lastChangeAt = MutableStateFlow(prefs.getLong(KEY_LAST_CHANGE, 0L))
    val lastChangeAt: StateFlow<Long> = _lastChangeAt.asStateFlow()

    fun setTimer(settings: TimerSettings) {
        prefs.edit().putString(KEY_TIMER, gson.toJson(settings)).apply()
        _timer.value = settings
    }

    fun setTriggers(triggers: ChangeTriggers) {
        prefs.edit().putString(KEY_TRIGGERS, gson.toJson(triggers)).apply()
        _triggers.value = triggers
    }

    fun setGestures(settings: GestureSettings) {
        prefs.edit().putString(KEY_GESTURES, gson.toJson(settings)).apply()
        _gestures.value = settings
    }

    fun setActiveAlbumId(id: String?) {
        prefs.edit().putString(KEY_ALBUM, id).apply()
        _activeAlbumId.value = id
    }

    fun setActiveIndex(index: Int) {
        val safe = index.coerceAtLeast(0)
        prefs.edit().putInt(KEY_INDEX, safe).apply()
        _activeIndex.value = safe
    }

    fun setLockAlbumId(id: String?) {
        prefs.edit().putString(KEY_LOCK_ALBUM, id).apply()
        _lockAlbumId.value = id
    }

    fun setLockIndex(index: Int) {
        val safe = index.coerceAtLeast(0)
        prefs.edit().putInt(KEY_LOCK_INDEX, safe).apply()
        _lockIndex.value = safe
    }

    fun setRandom(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RANDOM, enabled).apply()
        _random.value = enabled
    }

    fun setTarget(target: WallpaperTarget) {
        prefs.edit().putString(KEY_TARGET, target.name).apply()
        _target.value = target
    }

    /** OEM (MIUI) ana ekran değişince kilidi de ezerse geri yüklemek için */
    fun lastLockUri(): String? = prefs.getString(KEY_LAST_LOCK_URI, null)

    fun setLastLockUri(uri: String?) {
        prefs.edit().putString(KEY_LAST_LOCK_URI, uri).apply()
    }

    fun setDisplayFit(settings: DisplayFitSettings) {
        prefs.edit().putString(KEY_DISPLAY_FIT, gson.toJson(settings)).apply()
        _displayFit.value = settings
    }

    fun setEffects(effects: ImageEffects) {
        prefs.edit().putString(KEY_EFFECTS, gson.toJson(effects)).apply()
        _effects.value = effects
    }

    fun setBehavior(settings: BehaviorSettings) {
        prefs.edit().putString(KEY_BEHAVIOR, gson.toJson(settings)).apply()
        _behavior.value = settings
    }

    fun setDayPart(settings: DayPartAlbums) {
        prefs.edit().putString(KEY_DAY_PART, gson.toJson(settings)).apply()
        _dayPart.value = settings
    }

    fun markChangedNow() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_CHANGE, now).apply()
        _lastChangeAt.value = now
    }

    fun exportSnapshotJson(albumsJson: String): String {
        val map = linkedMapOf(
            "version" to 1,
            "timer" to _timer.value,
            "triggers" to _triggers.value,
            "gestures" to _gestures.value,
            "activeAlbumId" to _activeAlbumId.value,
            "activeIndex" to _activeIndex.value,
            "lockAlbumId" to _lockAlbumId.value,
            "lockIndex" to _lockIndex.value,
            "random" to _random.value,
            "target" to _target.value.name,
            "displayFit" to _displayFit.value,
            "effects" to _effects.value,
            "behavior" to _behavior.value,
            "dayPart" to _dayPart.value,
            "albums" to gson.fromJson(albumsJson, Any::class.java)
        )
        return gson.toJson(map)
    }

    fun importSnapshotJson(json: String): Boolean {
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            val root = gson.fromJson(json, Map::class.java) as Map<String, Any?>
            root["timer"]?.let {
                setTimer(gson.fromJson(gson.toJson(it), TimerSettings::class.java))
            }
            root["triggers"]?.let {
                setTriggers(gson.fromJson(gson.toJson(it), ChangeTriggers::class.java))
            }
            root["gestures"]?.let {
                setGestures(gson.fromJson(gson.toJson(it), GestureSettings::class.java))
            }
            root["displayFit"]?.let {
                setDisplayFit(gson.fromJson(gson.toJson(it), DisplayFitSettings::class.java))
            }
            root["effects"]?.let {
                setEffects(gson.fromJson(gson.toJson(it), ImageEffects::class.java))
            }
            root["behavior"]?.let {
                setBehavior(gson.fromJson(gson.toJson(it), BehaviorSettings::class.java))
            }
            root["dayPart"]?.let {
                setDayPart(gson.fromJson(gson.toJson(it), DayPartAlbums::class.java))
            }
            (root["activeAlbumId"] as? String)?.let { setActiveAlbumId(it) }
            (root["lockAlbumId"] as? String)?.let { setLockAlbumId(it) }
            ((root["activeIndex"] as? Number)?.toInt())?.let { setActiveIndex(it) }
            ((root["lockIndex"] as? Number)?.toInt())?.let { setLockIndex(it) }
            (root["random"] as? Boolean)?.let { setRandom(it) }
            (root["target"] as? String)?.let {
                runCatching { setTarget(WallpaperTarget.valueOf(it)) }
            }
            true
        }.getOrDefault(false)
    }

    private fun loadTimer(): TimerSettings {
        val json = prefs.getString(KEY_TIMER, null) ?: return TimerSettings()
        return runCatching { gson.fromJson(json, TimerSettings::class.java) }
            .getOrDefault(TimerSettings())
    }

    private fun loadTriggers(): ChangeTriggers {
        val json = prefs.getString(KEY_TRIGGERS, null) ?: return ChangeTriggers()
        return runCatching { gson.fromJson(json, ChangeTriggers::class.java) }
            .getOrDefault(ChangeTriggers())
    }

    private fun loadGestures(): GestureSettings {
        val json = prefs.getString(KEY_GESTURES, null) ?: return GestureSettings()
        return runCatching { gson.fromJson(json, GestureSettings::class.java) }
            .getOrDefault(GestureSettings())
    }

    private fun loadTarget(): WallpaperTarget {
        val name = prefs.getString(KEY_TARGET, WallpaperTarget.BOTH.name)
        return runCatching { WallpaperTarget.valueOf(name!!) }.getOrDefault(WallpaperTarget.BOTH)
    }

    private fun loadDisplayFit(): DisplayFitSettings {
        // v3: ekranı doldur (FILL) — FIT üst/alt siyah şerit bırakıyordu
        if (!prefs.getBoolean(KEY_FIT_MIGRATED_V3, false)) {
            val migrated = DisplayFitSettings(
                fitMode = FitMode.FILL,
                autoRotateDimsForLandscape = false
            )
            prefs.edit()
                .putBoolean(KEY_FIT_MIGRATED_V3, true)
                .putBoolean(KEY_FIT_MIGRATED_V2, true)
                .putString(KEY_DISPLAY_FIT, gson.toJson(migrated))
                .apply()
            return migrated
        }
        val json = prefs.getString(KEY_DISPLAY_FIT, null) ?: return DisplayFitSettings()
        return runCatching { gson.fromJson(json, DisplayFitSettings::class.java) }
            .getOrDefault(DisplayFitSettings())
    }

    private fun loadEffects(): ImageEffects {
        val json = prefs.getString(KEY_EFFECTS, null) ?: return ImageEffects()
        return runCatching { gson.fromJson(json, ImageEffects::class.java) }
            .getOrDefault(ImageEffects())
    }

    private fun loadBehavior(): BehaviorSettings {
        val json = prefs.getString(KEY_BEHAVIOR, null) ?: return BehaviorSettings()
        return runCatching { gson.fromJson(json, BehaviorSettings::class.java) }
            .getOrDefault(BehaviorSettings())
    }

    private fun loadDayPart(): DayPartAlbums {
        val json = prefs.getString(KEY_DAY_PART, null) ?: return DayPartAlbums()
        return runCatching { gson.fromJson(json, DayPartAlbums::class.java) }
            .getOrDefault(DayPartAlbums())
    }

    companion object {
        private const val PREFS = "wallpaper_settings"
        private const val KEY_TIMER = "timer"
        private const val KEY_TRIGGERS = "triggers"
        private const val KEY_GESTURES = "gestures"
        private const val KEY_ALBUM = "active_album"
        private const val KEY_INDEX = "active_index"
        private const val KEY_LOCK_ALBUM = "lock_album"
        private const val KEY_LOCK_INDEX = "lock_index"
        private const val KEY_RANDOM = "random"
        private const val KEY_TARGET = "target"
        private const val KEY_DISPLAY_FIT = "display_fit"
        private const val KEY_FIT_MIGRATED_V2 = "fit_migrated_v2"
        private const val KEY_FIT_MIGRATED_V3 = "fit_migrated_v3"
        private const val KEY_LAST_LOCK_URI = "last_lock_uri"
        private const val KEY_EFFECTS = "effects"
        private const val KEY_BEHAVIOR = "behavior"
        private const val KEY_DAY_PART = "day_part"
        private const val KEY_LAST_CHANGE = "last_change_at"

        @Volatile
        private var instance: WallpaperSettingsRepository? = null

        fun getInstance(context: Context): WallpaperSettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: WallpaperSettingsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
