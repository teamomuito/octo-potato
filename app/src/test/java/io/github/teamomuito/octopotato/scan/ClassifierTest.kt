package io.github.teamomuito.octopotato.scan

import io.github.teamomuito.octopotato.data.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassifierTest {

    private fun kind(text: String, vararg codes: FoundCode) = Classifier.classify(text, codes.toList())

    @Test fun `sms style login codes`() {
        assertEquals(Kind.CODE, kind("G-482913 is your Google verification code."))
        assertEquals(Kind.CODE, kind("Your Uber code is 4829. Never share this code."))
        assertEquals(Kind.CODE, kind("Use 381 204 as your one-time password for Acme Bank"))
        assertEquals(Kind.CODE, kind("Seu código de verificação é 583920"))
        assertEquals(Kind.CODE, kind("Tu código es 7731"))
    }

    @Test fun `code words without a code are normal`() {
        assertEquals(Kind.NORMAL, kind("Enter the verification code we sent to your phone"))
        assertEquals(Kind.NORMAL, kind("Your verification code expired in 2024, request a new one"))
    }

    @Test fun `loose phrases need the number close by`() {
        val far = "your code review is done. " + "blah ".repeat(20) + "ticket 4821"
        assertEquals(Kind.NORMAL, kind(far))
    }

    @Test fun `everyday screenshots stay normal`() {
        assertEquals(Kind.NORMAL, kind("see you at 7! bring snacks"))
        assertEquals(Kind.NORMAL, kind("Order #88213 total 24.99 delivered on 12 March 2025"))
        assertEquals(Kind.NORMAL, kind(""))
    }

    @Test fun `big qr codes count, tiny ones dont`() {
        assertEquals(Kind.QR, kind("scan to pay", FoundCode(CodeFormat.QR, "https://pay.example/abc", 0.18f)))
        assertEquals(Kind.NORMAL, kind("some article", FoundCode(CodeFormat.QR, "https://example.com", 0.004f)))
    }

    @Test fun `boarding pass barcode`() {
        val bcbp = "M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 326J001A0025 100"
        assertEquals(Kind.BOARDING, kind("", FoundCode(CodeFormat.AZTEC, bcbp, 0.1f)))
    }

    @Test fun `boarding pass text`() {
        assertEquals(Kind.BOARDING, kind("BOARDING PASS\nLIS → OPO\nGate B22  Seat 14A"))
        assertEquals(Kind.BOARDING, kind("Cartão de embarque TP1942 Portão 12"))
        assertEquals(Kind.BOARDING, kind("Flight TP 1942\nGate 12  Seat 3C", FoundCode(CodeFormat.PDF417, "xyz", 0.05f)))
    }

    @Test fun `a single flight word next to a code is not enough`() {
        assertEquals(Kind.QR, kind("our wifi, terminal 2 lounge", FoundCode(CodeFormat.QR, "WIFI:S:x;;", 0.2f)))
    }
}
