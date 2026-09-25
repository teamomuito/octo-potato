package io.github.teamomuito.octopotato.data

object SearchQuery {
    private val word = Regex("[\\p{L}\\p{N}]+")

    /**
     * Turns whatever was typed into an FTS4 MATCH string, or null if there's nothing to look for.
     * Every word becomes a prefix match, so "board" already finds "boarding".
     */
    fun toMatch(input: String): String? {
        val words = word.findAll(input).map { it.value.lowercase() }.take(8).toList()
        if (words.isEmpty()) return null
        return words.joinToString(" ") { "$it*" }
    }
}
