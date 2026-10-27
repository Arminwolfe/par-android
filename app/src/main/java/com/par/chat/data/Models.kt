package com.par.chat.data

import kotlinx.serialization.Serializable

@Serializable
data class InvitePayload(
    val v: Int = 1,
    val name: String,
    val network_id: String,
    val code: String,
    val addrs: List<String>,
    val pw: Boolean = false,
)

@Serializable
data class UserInfo(
    val id: String,
    val display_name: String,
    val role: String = "member",
    val online: Boolean = false,
    val pending: Boolean = false,
)

@Serializable
data class GroupInfo(val id: String, val name: String, val member_count: Long = 0)

@Serializable
data class FileInfo(val id: String, val name: String, val mime: String = "", val size: Long = 0, val kind: String = "file")

@Serializable
data class Msg(
    val seq: Long,
    val id: String,
    val sender_id: String,
    val recipient_id: String? = null,
    val group_id: String? = null,
    val body: String = "",
    val reply_to_id: String? = null,
    val status: String = "sent",
    val created_at: String = "",
    val edited_at: String? = null,
    val deleted: Boolean = false,
    val file: FileInfo? = null,
)

@Serializable
data class NetworkInfo(
    val network_id: String,
    val network_name: String,
    val password_required: Boolean = false,
    val created_at: String = "",
    val connected_users: Long = 0,
)

@Serializable
data class HealthResp(val status: String, val service: String, val uptime_secs: Long = 0)

@Serializable
data class JoinReq(val display_name: String, val invitation_code: String, val password: String? = null)

@Serializable
data class JoinResp(val token: String, val user: UserInfo, val network: NetworkInfo, val pending: Boolean = false)

@Serializable
data class Session(
    val server: String,
    val token: String,
    val userId: String,
    val userName: String,
    val role: String,
    val networkName: String,
    val networkId: String,
)

/** Result of testing one address of an invitation. */
data class AddrTest(val addr: String, val ok: Boolean, val latencyMs: Long = 0, val errorCode: String? = null)

class ApiException(val code: String, message: String) : Exception(message)

/** Key of a conversation: `u:<userId>` (private chat) or `g:<groupId>`. */
fun convOf(m: Msg, me: String): String =
    if (m.group_id != null) "g:${m.group_id}" else "u:${if (m.sender_id == me) m.recipient_id else m.sender_id}"
