package io.github.teamomuito.octopotato.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class TidySettings(
    val enabled: Boolean = true,
    val days: Int = 7,
    val qr: Boolean = true,
    val boarding: Boolean = true,
    val codes: Boolean = true,
) {
    fun covers(kind: Kind): Boolean = enabled && when (kind) {
        Kind.QR -> qr
        Kind.BOARDING -> boarding
        Kind.CODE -> codes
        Kind.NORMAL -> false
    }
}

/** Running total of what swipe cleanup has deleted. */
data class Freed(val bytes: Long = 0, val items: Int = 0)

object Prefs {
    private lateinit var prefs: SharedPreferences
    private val _tidy = MutableStateFlow(TidySettings())
    val tidy: StateFlow<TidySettings> = _tidy

    private val _freed = MutableStateFlow(Freed())
    val freed: StateFlow<Freed> = _freed

    /** Running total of what the cleaner has removed. */
    private val _cleaned = MutableStateFlow(0L)
    val cleaned: StateFlow<Long> = _cleaned

    /** Swipe deletes skip the trash and free the space right away. */
    private val _skipTrash = MutableStateFlow(false)
    val skipTrash: StateFlow<Boolean> = _skipTrash

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("octo", Context.MODE_PRIVATE)
        _tidy.value = TidySettings(
            enabled = prefs.getBoolean("tidy", true),
            days = prefs.getInt("days", 7),
            qr = prefs.getBoolean("qr", true),
            boarding = prefs.getBoolean("boarding", true),
            codes = prefs.getBoolean("codes", true),
        )
        _freed.value = Freed(prefs.getLong("freedBytes", 0), prefs.getInt("freedItems", 0))
        _skipTrash.value = prefs.getBoolean("skipTrash", false)
        _cleaned.value = prefs.getLong("cleanedBytes", 0)
    }

    @Synchronized
    fun addCleaned(bytes: Long) {
        if (bytes <= 0) return
        _cleaned.value += bytes
        prefs.edit().putLong("cleanedBytes", _cleaned.value).apply()
    }

    @Synchronized
    fun addFreed(bytes: Long, items: Int) {
        val next = Freed(_freed.value.bytes + bytes, _freed.value.items + items)
        _freed.value = next
        prefs.edit().putLong("freedBytes", next.bytes).putInt("freedItems", next.items).apply()
    }

    fun setSkipTrash(on: Boolean) {
        _skipTrash.value = on
        prefs.edit().putBoolean("skipTrash", on).apply()
    }

    fun updateTidy(change: (TidySettings) -> TidySettings) {
        val next = change(_tidy.value)
        _tidy.value = next
        prefs.edit()
            .putBoolean("tidy", next.enabled)
            .putInt("days", next.days)
            .putBoolean("qr", next.qr)
            .putBoolean("boarding", next.boarding)
            .putBoolean("codes", next.codes)
            .apply()
    }
}
