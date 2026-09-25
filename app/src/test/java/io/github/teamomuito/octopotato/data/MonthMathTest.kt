package io.github.teamomuito.octopotato.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class MonthMathTest {

    private val march = YearMonth.of(2021, 3)
    private val may = YearMonth.of(2021, 5)
    private val jan = YearMonth.of(2019, 1)

    private fun item(id: Long, month: YearMonth, taken: Long, size: Long, video: Boolean = false) =
        MediaEntry(id, "content://media/external/images/media/$id", video, taken, size, 0, month)

    private val items = listOf(
        item(1, march, taken = 30, size = 100),
        item(2, march, taken = 10, size = 200),
        item(3, march, taken = 20, size = 300, video = true),
        item(4, may, taken = 40, size = 50),
        item(5, jan, taken = 5, size = 70),
    )

    @Test fun `months come oldest first with counts and sizes`() {
        val months = MonthMath.summarize(items, emptyMap())
        assertEquals(listOf(jan, march, may), months.map { it.month })
        val m = months[1]
        assertEquals(2, m.photos)
        assertEquals(1, m.videos)
        assertEquals(600L, m.bytes)
        assertEquals(0, m.reviewed)
        assertFalse(m.done)
    }

    @Test fun `verdicts count as reviewed, deletes add up their size`() {
        val verdicts = mapOf(1L to Verdict.KEEP, 3L to Verdict.DELETE, 2L to Verdict.DELETE)
        val m = MonthMath.summarize(items, verdicts).first { it.month == march }
        assertEquals(3, m.reviewed)
        assertEquals(2, m.toDelete)
        assertEquals(500L, m.toDeleteBytes)
        assertEquals(1, m.kept)
        assertTrue(m.done)
    }

    @Test fun `deck is what's left, oldest first, and marked is what's going`() {
        val view = MonthMath.view(items, mapOf(2L to Verdict.DELETE), march)
        assertEquals(listOf(3L, 1L), view.deck.map { it.id })
        assertEquals(listOf(2L), view.marked.map { it.id })
        assertEquals(1, view.summary.reviewed)
    }

    @Test fun `a month with nothing left in it is simply empty`() {
        val view = MonthMath.view(items, emptyMap(), YearMonth.of(2030, 1))
        assertEquals(0, view.summary.total)
        assertTrue(view.summary.done)
        assertTrue(view.deck.isEmpty())
    }

    @Test fun `verdict ids round trip`() {
        Verdict.entries.forEach { assertEquals(it, Verdict.of(it.id)) }
        assertEquals(null, Verdict.of(99))
    }
}
