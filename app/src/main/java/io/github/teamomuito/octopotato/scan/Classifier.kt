package io.github.teamomuito.octopotato.scan

import io.github.teamomuito.octopotato.data.Kind
import kotlin.math.abs

enum class CodeFormat { QR, AZTEC, PDF417, DATA_MATRIX, OTHER }

/** A barcode spotted in a screenshot. [area] is how much of the image it covers, from 0 to 1. */
data class FoundCode(val format: CodeFormat, val raw: String?, val area: Float)

/**
 * Decides if a screenshot is one of the short-lived kinds: a qr code, a boarding pass or a
 * login code. Everything else is [Kind.NORMAL] and never gets tidied.
 *
 * When in doubt it says NORMAL. Wrongly calling something temporary could put a screenshot
 * someone cares about in the trash, wrongly calling it normal just means it sticks around.
 */
object Classifier {

    /** A qr code has to fill at least this much of the image. The tiny ones on web pages don't count. */
    const val MIN_QR_AREA = 0.02f

    fun classify(text: String, codes: List<FoundCode>): Kind {
        val t = text.lowercase()
        return when {
            isBoardingPass(t, codes) -> Kind.BOARDING
            isLoginCode(t) -> Kind.CODE
            codes.any { it.format == CodeFormat.QR && it.area >= MIN_QR_AREA } -> Kind.QR
            else -> Kind.NORMAL
        }
    }

    // IATA boarding pass barcode: "M1", 20 chars of name, e-ticket flag, 7 char booking ref, FROM, TO
    private val boardingBarcode = Regex("^M[1-9].{28}[A-Z]{3}[A-Z]{3}")

    private val boardingPhrases = phrases(
        "boarding pass", "boarding group", "boarding time", "boarding zone", "boarding begins",
        "cartão de embarque", "cartao de embarque", "tarjeta de embarque", "pase de abordar",
        "carte d'embarquement", "bordkarte",
    )

    private val flightWords = phrases(
        "gate", "seat", "flight", "boarding", "departs", "terminal",
        "portão", "portao", "assento", "voo", "embarque", "puerta", "asiento", "vuelo",
    )

    private fun isBoardingPass(t: String, codes: List<FoundCode>): Boolean {
        if (codes.any { it.raw != null && boardingBarcode.containsMatchIn(it.raw) }) return true
        if (boardingPhrases.containsMatchIn(t)) return true
        val ticketish = codes.any { it.format == CodeFormat.PDF417 || it.format == CodeFormat.AZTEC || it.area >= MIN_QR_AREA }
        return ticketish && flightWords.findAll(t).map { it.value }.distinct().count() >= 2
    }

    // Phrases that basically only show up next to a login code
    private val codePhrases = phrases(
        "verification code", "security code", "login code", "log in code", "sign-in code", "sign in code",
        "one-time password", "one time password", "one-time code", "one time code", "one-time passcode",
        "authentication code", "confirmation code", "2fa code", "otp", "passcode",
        "código de verificação", "codigo de verificacao", "código de segurança", "codigo de seguranca",
        "código de confirmação", "codigo de confirmacao", "código de acesso", "codigo de acesso",
        "código de verificación", "codigo de verificacion", "código de seguridad", "codigo de seguridad",
        "code de vérification", "bestätigungscode",
    )

    // Vaguer ones, so the number has to be right next to them
    private val looseCodePhrases = phrases(
        "your code", "code is", "enter code", "seu código", "seu codigo", "tu código", "tu codigo",
    )

    // 4 to 8 digits, or "123 456" / "123-456", not glued to other digits
    private val codeNumber = Regex("(?<!\\d)(?:\\d{4,8}|\\d{3}[ -]\\d{3})(?!\\d)")
    private val year = Regex("(19|20)\\d\\d")

    private fun isLoginCode(t: String): Boolean {
        val numbers = codeNumber.findAll(t).filterNot { year.matches(it.value) }.map { it.range.first }.toList()
        if (numbers.isEmpty()) return false
        fun near(phrases: Regex, window: Int) =
            phrases.findAll(t).any { phrase -> numbers.any { abs(it - phrase.range.first) <= window } }
        return near(codePhrases, 160) || near(looseCodePhrases, 50)
    }

    /** Matches any of the phrases as whole words. */
    private fun phrases(vararg list: String): Regex = Regex(
        list.joinToString("|", prefix = "(?<![\\p{L}\\p{N}])(?:", postfix = ")(?![\\p{L}\\p{N}])") { Regex.escape(it) }
    )
}
