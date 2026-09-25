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

object Prefs {
    private lateinit var prefs: SharedPreferences
    private val _tidy = MutableStateFlow(TidySettings())
    val tidy: StateFlow<TidySettings> = _tidy

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
