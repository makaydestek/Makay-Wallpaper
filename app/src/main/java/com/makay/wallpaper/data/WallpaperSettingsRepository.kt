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
import com.makay.wallpaper.domain.model.IntervalUnit
import com.makay.wallpaper.domain.model.PlayOrder
import com.makay.wallpaper.domain.model.ResolutionSource
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

    init {
        // Eski KEY_RANDOM ile timer.playOrder senkron kalsın (sıralı seçimi ezmesin)
        val order = _timer.value.effectivePlayOrder()
        val wantRandom = order == PlayOrder.RANDOM
        if (prefs.getBoolean(KEY_RANDOM, false) != wantRandom) {
            prefs.edit().putBoolean(KEY_RANDOM, wantRandom).commit()
        }
    }

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

    private val _random = MutableStateFlow(
        _timer.value.effectivePlayOrder() == PlayOrder.RANDOM
    )
    val random: StateFlow<Boolean> = _random.asStateFlow()

    /** Zamanlayıcı değişiminde kullanılacak kesin sıra (global random bayrağı yok sayılır). */
    fun resolvedPlayOrder(): PlayOrder = _timer.value.effectivePlayOrder()

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

    /** Ana sayfada ayrı Zamanlayıcı menü öğesini göster (varsayılan kapalı — Albümler’den yönetilir). */
    private val _showTimerOnHome = MutableStateFlow(prefs.getBoolean(KEY_SHOW_TIMER_HOME, false))
    val showTimerOnHome: StateFlow<Boolean> = _showTimerOnHome.asStateFlow()

    /** Rastgele shuffle kuyruğu — albüm bitene kadar aynı görsel tekrarlanmaz. */
    @Volatile private var shuffleAlbumId: String? = null
    private val shuffleQueue = ArrayDeque<Int>()
    private val shuffleLock = Any()

    fun setTimer(settings: TimerSettings) {
        val order = settings.effectivePlayOrder()
        val normalized = settings.copy(
            random = order == PlayOrder.RANDOM,
            playOrder = order
        )
        // commit: worker / alarm hemen güncel playOrder okusun
        prefs.edit().putString(KEY_TIMER, gson.toJson(normalized)).commit()
        _timer.value = normalized
        setRandom(order == PlayOrder.RANDOM)
        if (order != PlayOrder.RANDOM) {
            clearShuffleQueue()
        }
    }

    fun setPlayOrder(order: PlayOrder) {
        val current = _timer.value
        setTimer(
            current.copy(
                playOrder = order,
                random = order == PlayOrder.RANDOM
            )
        )
    }

    fun setShowTimerOnHome(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_TIMER_HOME, show).apply()
        _showTimerOnHome.value = show
    }

    fun clearShuffleQueue() {
        synchronized(shuffleLock) {
            shuffleQueue.clear()
            shuffleAlbumId = null
        }
    }

    /**
     * Karışık sırada sonraki indeks. Liste bitince yeniden karıştırılır;
     * mümkünse mevcut indeks peş peşe seçilmez.
     */
    fun nextShuffledIndex(albumId: String, size: Int, currentIndex: Int): Int {
        if (size <= 0) return 0
        if (size == 1) return 0
        synchronized(shuffleLock) {
            if (shuffleAlbumId != albumId || shuffleQueue.isEmpty()) {
                shuffleAlbumId = albumId
                val indices = (0 until size).toMutableList()
                indices.shuffle()
                // Mevcut görseli hemen başa koyma
                if (indices.first() == currentIndex && indices.size > 1) {
                    indices.removeAt(0)
                    indices.add(currentIndex)
                }
                shuffleQueue.clear()
                shuffleQueue.addAll(indices)
            }
            var next = shuffleQueue.removeFirst()
            if (next == currentIndex && shuffleQueue.isNotEmpty()) {
                shuffleQueue.addLast(next)
                next = shuffleQueue.removeFirst()
            }
            return next.coerceIn(0, size - 1)
        }
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
        if (id != _activeAlbumId.value) clearShuffleQueue()
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
        prefs.edit().putBoolean(KEY_RANDOM, enabled).commit()
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
            // random bayrağı timer.playOrder’dan gelir; kök "random" sıralı seçimi ezmesin
            if (root["timer"] == null) {
                (root["random"] as? Boolean)?.let { setRandom(it) }
            } else {
                setRandom(_timer.value.effectivePlayOrder() == PlayOrder.RANDOM)
            }
            (root["target"] as? String)?.let {
                runCatching { setTarget(WallpaperTarget.valueOf(it)) }
            }
            true
        }.getOrDefault(false)
    }

    private fun loadTimer(): TimerSettings {
        val json = prefs.getString(KEY_TIMER, null) ?: return TimerSettings()
        return runCatching {
            val obj = gson.fromJson(json, com.google.gson.JsonObject::class.java)
            val enabled = obj.get("enabled")?.asBoolean ?: false
            val intervalValue = obj.get("intervalValue")?.asInt ?: 30
            val unit = obj.get("unit")?.asString
                ?.let { runCatching { IntervalUnit.valueOf(it) }.getOrNull() }
                ?: IntervalUnit.MINUTES
            val legacyRandom = obj.get("random")?.asBoolean ?: false
            val playOrder = obj.get("playOrder")?.asString
                ?.let { runCatching { PlayOrder.valueOf(it) }.getOrNull() }
                ?: if (legacyRandom) PlayOrder.RANDOM else PlayOrder.SEQUENTIAL
            TimerSettings(
                enabled = enabled,
                intervalValue = intervalValue,
                unit = unit,
                random = playOrder == PlayOrder.RANDOM,
                playOrder = playOrder
            )
        }.getOrDefault(TimerSettings())
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
        // v4: MIUI yarım ekran / siyah alt — FILL + cihaz çözünürlüğü
        if (!prefs.getBoolean(KEY_FIT_MIGRATED_V4, false)) {
            val migrated = DisplayFitSettings(
                fitMode = FitMode.FILL,
                resolutionSource = ResolutionSource.DEVICE,
                autoRotateDimsForLandscape = false
            )
            prefs.edit()
                .putBoolean(KEY_FIT_MIGRATED_V4, true)
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
        private const val KEY_SHOW_TIMER_HOME = "show_timer_on_home"
        private const val KEY_TARGET = "target"
        private const val KEY_DISPLAY_FIT = "display_fit"
        private const val KEY_FIT_MIGRATED_V2 = "fit_migrated_v2"
        private const val KEY_FIT_MIGRATED_V3 = "fit_migrated_v3"
        private const val KEY_FIT_MIGRATED_V4 = "fit_migrated_v4"
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
