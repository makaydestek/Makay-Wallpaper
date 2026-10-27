package com.makay.wallpaper.domain.model

data class WallpaperImage(
    val uri: String,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    /** Albüme eklenme zamanı (sıralama için). 0 = bilinmiyor → liste indeksi kullanılır. */
    val addedAt: Long = 0L
) {
    fun cropRectNormalized(): android.graphics.RectF =
        android.graphics.RectF(
            cropLeft.coerceIn(0f, 1f),
            cropTop.coerceIn(0f, 1f),
            cropRight.coerceIn(0f, 1f).coerceAtLeast(cropLeft + 0.01f),
            cropBottom.coerceIn(0f, 1f).coerceAtLeast(cropTop + 0.01f)
        )
}

data class Album(
    val id: String,
    val name: String,
    val images: List<WallpaperImage> = emptyList(),
    val folderPath: String? = null,
    val watchFolder: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val imageUris: List<String> get() = images.map { it.uri }
}

enum class IntervalUnit {
    SECONDS, MINUTES, HOURS, DAYS
}

/** Otomatik değişimde görsel sırası. */
enum class PlayOrder {
    /** Albüm listesi sırası */
    SEQUENTIAL,
    /** Karışık sıra (tekrarsız shuffle) */
    RANDOM,
    /** En yeniden en eskiye */
    NEWEST_FIRST,
    /** En eskiden en yeniye */
    OLDEST_FIRST
}

data class TimerSettings(
    val enabled: Boolean = false,
    val intervalValue: Int = 30,
    val unit: IntervalUnit = IntervalUnit.MINUTES,
    /** Eski JSON uyumluluğu — [playOrder] tercih edilir */
    val random: Boolean = false,
    val playOrder: PlayOrder = PlayOrder.SEQUENTIAL
) {
    fun effectivePlayOrder(): PlayOrder = when {
        // playOrder alanı varsa ona güven; random yalnız eski JSON yedekleri için
        playOrder != PlayOrder.SEQUENTIAL -> playOrder
        random -> PlayOrder.RANDOM
        else -> PlayOrder.SEQUENTIAL
    }

    fun intervalMillis(): Long {
        val v = intervalValue.coerceAtLeast(1).toLong()
        return when (unit) {
            IntervalUnit.SECONDS -> v * 1_000L
            IntervalUnit.MINUTES -> v * 60_000L
            IntervalUnit.HOURS -> v * 3_600_000L
            IntervalUnit.DAYS -> v * 86_400_000L
        }
    }
}

data class ChangeTriggers(
    val onScreenOn: Boolean = false,
    val onScreenOff: Boolean = false,
    val onUnlock: Boolean = false,
    /** Ana ekran görünür olduğunda (canlı duvar kağıdı) */
    val onHomeShown: Boolean = false,
    /** Ana ekran gizlendiğinde / uygulama açıldığında (canlı duvar kağıdı) */
    val onHomeHidden: Boolean = false
)

enum class GestureAction {
    NEXT, PREV, NEXT_ALBUM, PREV_ALBUM, NONE
}

data class GestureSettings(
    val enabled: Boolean = true,
    val doubleTapAction: GestureAction = GestureAction.NEXT,
    val tripleTapAction: GestureAction = GestureAction.NEXT_ALBUM
)

enum class ScheduleType {
    WALLPAPER, ALBUM
}

data class Schedule(
    val id: String,
    val type: ScheduleType = ScheduleType.WALLPAPER,
    val hour: Int = 8,
    val minute: Int = 0,
    /** 1=Pazar … 7=Cumartesi (Calendar.DAY_OF_WEEK); boş = her gün */
    val daysOfWeek: Set<Int> = emptySet(),
    /** 1–366; 0 = kullanılmaz */
    val dayOfYear: Int = 0,
    val enabled: Boolean = true,
    val targetAlbumId: String? = null,
    val random: Boolean = false
)

enum class WallpaperTarget {
    HOME, LOCK, BOTH
}

/**
 * Sosyal medya / internet görsellerinin ekrana oturtulma şekli.
 * FILL = ekranı tamamen kaplar (kenarlar kırpılabilir)
 * FIT = tamamı görünür (şerit kalabilir)
 * STRETCH = en-boy bozulabilir şekilde zorla doldur
 */
enum class FitMode {
    FILL, FIT, STRETCH
}

enum class ResolutionSource {
    /** Anlık ekran çözünürlüğü */
    DEVICE,
    /** Kullanıcı tanımlı dikey çözünürlük (yatay otomatik çevrilir) */
    CUSTOM
}

data class DisplayFitSettings(
    /** FILL: ekranı doldur (varsayılan). FIT: tamamı görünsün (şerit kalabilir). */
    val fitMode: FitMode = FitMode.FILL,
    val resolutionSource: ResolutionSource = ResolutionSource.DEVICE,
    /** Örn. 1220 — dikey genişlik */
    val customWidth: Int = 1220,
    /** Örn. 2712 — dikey yükseklik */
    val customHeight: Int = 2712,
    /** Yatay görsellerde W↔H değiştir (telefon duvar kağıdında genelde kapalı tutulmalı) */
    val autoRotateDimsForLandscape: Boolean = false,
    /** FIT modunda boşluk rengi (ARGB) */
    val letterboxColor: Int = 0xFF000000.toInt()
) {
    fun portraitSize(): Pair<Int, Int> =
        customWidth.coerceAtLeast(240) to customHeight.coerceAtLeast(240)
}

/** Görsel efektler — ikon okunabilirliği / stil */
data class ImageEffects(
    /** 0–25 yaklaşık bulanıklık */
    val blurRadius: Float = 0f,
    /** -0.5…0.5 karart / aydınlat */
    val brightness: Float = 0f,
    /** 0–1 kenar karartma */
    val vignette: Float = 0f,
    val grayscale: Boolean = false
) {
    fun isIdentity(): Boolean =
        blurRadius <= 0.01f && brightness == 0f && vignette <= 0.01f && !grayscale
}

enum class TransitionStyle {
    NONE, FADE
}

data class BehaviorSettings(
    /** Yatay (landscape) iken otomatik değişimi durdur */
    val pauseInLandscape: Boolean = true,
    val transition: TransitionStyle = TransitionStyle.FADE
)

/**
 * Günün saatine göre albüm.
 * Sabah 06–12, Öğle 12–18, Akşam 18–22, Gece 22–06
 */
data class DayPartAlbums(
    val enabled: Boolean = false,
    val morningAlbumId: String? = null,
    val afternoonAlbumId: String? = null,
    val eveningAlbumId: String? = null,
    val nightAlbumId: String? = null
) {
    fun albumIdForHour(hour: Int): String? {
        if (!enabled) return null
        return when (hour) {
            in 6 until 12 -> morningAlbumId
            in 12 until 18 -> afternoonAlbumId
            in 18 until 22 -> eveningAlbumId
            else -> nightAlbumId
        }
    }
}
