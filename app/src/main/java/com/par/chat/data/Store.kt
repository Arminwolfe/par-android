package com.par.chat.data

import android.content.Context
import kotlinx.serialization.encodeToString

data class IceConfig(
    val stun: String = "stun:stun.l.google.com:19302",
    val turnUrl: String = "",
    val turnUser: String = "",
    val turnPass: String = "",
)

/** Small key-value storage in the app's private area. */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("par", Context.MODE_PRIVATE)

    fun loadSession(): Session? = try {
        p.getString("session", null)?.let { Net.json.decodeFromString<Session>(it) }
    } catch (e: Exception) {
        null
    }

    fun saveSession(s: Session?) {
        p.edit().apply { if (s == null) remove("session") else putString("session", Net.json.encodeToString(s)) }.apply()
    }

    fun lastSeq(s: Session): Long = p.getLong("seq:${s.networkId}:${s.userId}", 0)
    fun saveSeq(s: Session, v: Long) = p.edit().putLong("seq:${s.networkId}:${s.userId}", v).apply()

    fun ice(): IceConfig = IceConfig(
        p.getString("stun", IceConfig().stun) ?: "",
        p.getString("turnUrl", "") ?: "",
        p.getString("turnUser", "") ?: "",
        p.getString("turnPass", "") ?: "",
    )

    fun saveIce(c: IceConfig) = p.edit()
        .putString("stun", c.stun).putString("turnUrl", c.turnUrl)
        .putString("turnUser", c.turnUser).putString("turnPass", c.turnPass).apply()
}
