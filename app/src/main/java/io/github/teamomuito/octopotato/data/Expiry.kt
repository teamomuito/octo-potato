package io.github.teamomuito.octopotato.data

import kotlin.math.ceil

object Expiry {
    const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * The clock starts at whichever is later: when the screenshot was taken, or when the app
     * first saw it. Otherwise installing the app would flag last year's boarding passes right away.
     */
    fun at(taken: Long, found: Long, days: Int): Long = maxOf(taken, found) + days * DAY_MS

    /** Whole days left, rounded up. Zero or less means it's due. */
    fun daysLeft(expiresAt: Long, now: Long): Int = ceil((expiresAt - now).toDouble() / DAY_MS).toInt()

    fun label(daysLeft: Int): String = when {
        daysLeft <= 0 -> "ready to tidy"
        daysLeft == 1 -> "leaves tomorrow"
        else -> "leaves in $daysLeft days"
    }

    fun spanLabel(days: Int): String = when (days) {
        1 -> "a day"
        7 -> "a week"
        14 -> "two weeks"
        else -> "$days days"
    }
}
