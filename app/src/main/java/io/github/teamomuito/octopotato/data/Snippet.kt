package io.github.teamomuito.octopotato.data

/** Splits an FTS snippet into plain and highlighted bits. The query wraps hits in [START] and [END]. */
object Snippet {
    const val START = '\u0001'
    const val END = '\u0002'

    data class Part(val text: String, val hit: Boolean)

    fun parts(snippet: String): List<Part> {
        val out = ArrayList<Part>()
        val current = StringBuilder()
        var hit = false
        for (c in snippet) {
            if (c == START || c == END) {
                if (current.isNotEmpty()) out += Part(current.toString(), hit)
                current.clear()
                hit = c == START
            } else {
                current.append(if (c == '\n') ' ' else c)
            }
        }
        if (current.isNotEmpty()) out += Part(current.toString(), hit)
        return out
    }
}
