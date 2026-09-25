package io.github.teamomuito.octopotato.data

import java.time.YearMonth

/** A photo or video on the phone. */
data class MediaEntry(
    val id: Long,
    val uri: String,
    val isVideo: Boolean,
    val taken: Long,
    val size: Long,
    val durationMs: Long,
    val month: YearMonth,
)

/** What someone swiped. [id] goes in the database, so never reuse one. */
enum class Verdict(val id: Int) {
    KEEP(1),
    DELETE(2);

    companion object {
        fun of(id: Int): Verdict? = entries.firstOrNull { it.id == id }
    }
}

data class MonthSummary(
    val month: YearMonth,
    val photos: Int,
    val videos: Int,
    val bytes: Long,
    val reviewed: Int,
    val toDelete: Int,
    val toDeleteBytes: Long,
) {
    val total: Int get() = photos + videos
    val kept: Int get() = reviewed - toDelete
    val done: Boolean get() = reviewed >= total
}

/** Everything the swipe screen needs for one month, worked out in one go so it never disagrees with itself. */
data class MonthView(val summary: MonthSummary, val deck: List<MediaEntry>, val marked: List<MediaEntry>)

object MonthMath {

    /** Oldest month first, since that's where the forgotten stuff lives. */
    fun summarize(items: List<MediaEntry>, verdicts: Map<Long, Verdict>): List<MonthSummary> =
        items.groupBy { it.month }
            .map { (month, list) -> summary(month, list, verdicts) }
            .sortedBy { it.month }

    fun view(items: List<MediaEntry>, verdicts: Map<Long, Verdict>, month: YearMonth): MonthView {
        val inMonth = items.filter { it.month == month }.sortedBy { it.taken }
        return MonthView(
            summary = summary(month, inMonth, verdicts),
            deck = inMonth.filter { it.id !in verdicts },
            marked = inMonth.filter { verdicts[it.id] == Verdict.DELETE },
        )
    }

    private fun summary(month: YearMonth, list: List<MediaEntry>, verdicts: Map<Long, Verdict>): MonthSummary {
        var photos = 0
        var videos = 0
        var bytes = 0L
        var reviewed = 0
        var toDelete = 0
        var toDeleteBytes = 0L
        for (m in list) {
            if (m.isVideo) videos++ else photos++
            bytes += m.size
            when (verdicts[m.id]) {
                Verdict.KEEP -> reviewed++
                Verdict.DELETE -> {
                    reviewed++
                    toDelete++
                    toDeleteBytes += m.size
                }
                null -> Unit
            }
        }
        return MonthSummary(month, photos, videos, bytes, reviewed, toDelete, toDeleteBytes)
    }
}
