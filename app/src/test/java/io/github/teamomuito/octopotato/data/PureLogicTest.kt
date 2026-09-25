package io.github.teamomuito.octopotato.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchQueryTest {
    @Test fun `words become prefix matches`() {
        assertEquals("boarding* pass*", SearchQuery.toMatch("  Boarding PASS! "))
        assertEquals("código* 123*", SearchQuery.toMatch("Código: 123"))
    }

    @Test fun `nothing to search`() {
        assertNull(SearchQuery.toMatch(""))
        assertNull(SearchQuery.toMatch("  !!! ?? "))
    }

    @Test fun `quotes and operators cannot sneak in`() {
        assertEquals("a* or* b*", SearchQuery.toMatch("\"a\" OR b*"))
    }
}

class ExpiryTest {
    private val day = Expiry.DAY_MS

    @Test fun `clock starts at the later of taken and found`() {
        assertEquals(10 * day + 7 * day, Expiry.at(taken = 2 * day, found = 10 * day, days = 7))
        assertEquals(10 * day + 7 * day, Expiry.at(taken = 10 * day, found = 2 * day, days = 7))
    }

    @Test fun `days left rounds up`() {
        assertEquals(1, Expiry.daysLeft(expiresAt = day, now = 1))
        assertEquals(0, Expiry.daysLeft(expiresAt = day, now = day))
        assertEquals(3, Expiry.daysLeft(expiresAt = 3 * day, now = 0))
    }

    @Test fun labels() {
        assertEquals("ready to tidy", Expiry.label(0))
        assertEquals("leaves tomorrow", Expiry.label(1))
        assertEquals("leaves in 4 days", Expiry.label(4))
    }
}

class SnippetTest {
    @Test fun `splits hits from plain text`() {
        val s = "your ${Snippet.START}code${Snippet.END} is\n4821"
        assertEquals(
            listOf(Snippet.Part("your ", false), Snippet.Part("code", true), Snippet.Part(" is 4821", false)),
            Snippet.parts(s),
        )
    }

    @Test fun `no markers`() {
        assertEquals(listOf(Snippet.Part("plain", false)), Snippet.parts("plain"))
    }
}
