package com.makay.wallpaper.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.makay.wallpaper.domain.model.Schedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class ScheduleRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _schedules = MutableStateFlow(load())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()

    fun getAll(): List<Schedule> = _schedules.value

    fun getById(id: String): Schedule? = _schedules.value.find { it.id == id }

    fun add(schedule: Schedule): Schedule {
        val withId = if (schedule.id.isBlank()) schedule.copy(id = UUID.randomUUID().toString()) else schedule
        persist(_schedules.value + withId)
        return withId
    }

    fun update(schedule: Schedule) {
        persist(_schedules.value.map { if (it.id == schedule.id) schedule else it })
    }

    fun delete(id: String) {
        persist(_schedules.value.filterNot { it.id == id })
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val s = getById(id) ?: return
        update(s.copy(enabled = enabled))
    }

    private fun load(): List<Schedule> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<Schedule>>() {}.type
            gson.fromJson<List<Schedule>>(json, type).orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun persist(list: List<Schedule>) {
        prefs.edit().putString(KEY, gson.toJson(list)).apply()
        _schedules.value = list
    }

    companion object {
        private const val PREFS = "schedule_prefs"
        private const val KEY = "schedules"

        @Volatile
        private var instance: ScheduleRepository? = null

        fun getInstance(context: Context): ScheduleRepository {
            return instance ?: synchronized(this) {
                instance ?: ScheduleRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
