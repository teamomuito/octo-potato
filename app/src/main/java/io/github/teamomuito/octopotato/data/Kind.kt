package io.github.teamomuito.octopotato.data

/**
 * What a screenshot turned out to be. [id] is what goes in the database, so never reuse one.
 * [searchWords] get added to the index so "boarding" finds boarding passes even when the
 * screenshot itself only says "gate 12".
 */
enum class Kind(val id: Int, val searchWords: String) {
    NORMAL(0, ""),
    QR(1, "qr code"),
    BOARDING(2, "boarding pass flight"),
    CODE(3, "verification login code otp");

    val temporary: Boolean get() = this != NORMAL

    companion object {
        fun of(id: Int): Kind = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}
