package com.colmenas.app

import java.nio.charset.StandardCharsets

/** NFC Forum text records include a status byte and a variable-length language code. */
fun decodeNfcText(payload: ByteArray): String? {
    if (payload.isEmpty()) return null
    val status = payload[0].toInt() and 0xff
    val offset = 1 + (status and 0x3f)
    if (offset >= payload.size) return null
    val charset = if (status and 0x80 == 0) StandardCharsets.UTF_8 else StandardCharsets.UTF_16
    return String(payload, offset, payload.size - offset, charset).trim().takeIf { it.isNotEmpty() }
}

fun normalizeHiveCode(value: String): String = value.trim().trimEnd('/').substringAfterLast('/')

fun nfcTagId(bytes: ByteArray): String? = bytes.takeIf { it.isNotEmpty() }
    ?.joinToString("") { "%02X".format(it.toInt() and 0xff) }
