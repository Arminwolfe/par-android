package com.par.chat.data

import android.util.Base64

/** Invitation links: `SPINV1:` + base64url(JSON). Same format as the desktop app. */
object Invite {
    private const val PREFIX = "SPINV1:"
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private val linkRegex = Regex("SPINV1:[A-Za-z0-9_-]+")
    private val netRegex = Regex("^SP-[$ALPHABET]{4}(-[$ALPHABET]{4}){3}$")
    private val addrRegex = Regex("""^(\[[0-9a-fA-F:]+]|[A-Za-z0-9.-]+):(\d{1,5})$""")

    fun validAddr(a: String): Boolean {
        val m = addrRegex.matchEntire(a.trim()) ?: return false
        return m.groupValues[2].toInt() in 1..65535
    }

    /** "ABCD efgh-jkmn pqrs" -> "ABCD-EFGH-JKMN-PQRS", or null if it is not a valid code. */
    fun normalizeCode(raw: String): String? {
        val c = raw.filter { !it.isWhitespace() && it != '-' }.uppercase()
        if (c.length != 16 || !c.all { it in ALPHABET }) return null
        return c.chunked(4).joinToString("-")
    }

    /** Accepts the bare link or a text that contains it (for example a shared message). */
    fun decode(text: String): InvitePayload? {
        val link = linkRegex.find(text.trim())?.value ?: return null
        if (link.length > 6000) return null
        return try {
            val bytes = Base64.decode(link.removePrefix(PREFIX), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val p = Net.json.decodeFromString<InvitePayload>(String(bytes, Charsets.UTF_8))
            val code = normalizeCode(p.code) ?: return null
            if (p.v != 1 || !netRegex.matches(p.network_id)) return null
            if (p.addrs.isEmpty() || p.addrs.size > 8 || !p.addrs.all(::validAddr)) return null
            p.copy(code = code)
        } catch (e: Exception) {
            null
        }
    }
}
