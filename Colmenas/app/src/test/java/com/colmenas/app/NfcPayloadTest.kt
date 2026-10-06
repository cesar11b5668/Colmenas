package com.colmenas.app

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets

class NfcPayloadTest {
    @Test fun readsVariableLengthLanguageCodes() {
        val payload = byteArrayOf(5) + "es-MXCOL-12345678".toByteArray()
        assertEquals("COL-12345678", decodeNfcText(payload))
    }
    @Test fun readsUtf16Text() {
        val payload = byteArrayOf(0x82.toByte()) + "es".toByteArray() + "COL-ABCDEF01".toByteArray(StandardCharsets.UTF_16)
        assertEquals("COL-ABCDEF01", decodeNfcText(payload))
    }
    @Test fun rejectsTruncatedTextRecords() {
        assertNull(decodeNfcText(byteArrayOf()))
        assertNull(decodeNfcText(byteArrayOf(6, 101, 115)))
    }
    @Test fun normalizesPlainAndUrlCodes() {
        assertEquals("COL-1234", normalizeHiveCode("  https://example.com/hives/COL-1234/  "))
        assertEquals("COL-1234", normalizeHiveCode(" COL-1234 "))
    }
    @Test fun formatsUnsignedTagBytesAndRejectsEmptyIds() {
        assertEquals("0080FF01", nfcTagId(byteArrayOf(0, 0x80.toByte(), 0xff.toByte(), 1)))
        assertNull(nfcTagId(byteArrayOf()))
    }
}
