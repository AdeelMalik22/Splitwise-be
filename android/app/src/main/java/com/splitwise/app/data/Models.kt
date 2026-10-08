package com.splitwise.app.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/** DRF returns decimals as strings by default, but tolerate plain JSON numbers too. */
object MoneySerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("Money", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String =
        ((decoder as JsonDecoder).decodeJsonElement() as JsonPrimitive).content
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

@Serializable data class Page<T>(val results: List<T> = emptyList())

@Serializable data class LoginRequest(val username: String, val password: String)
@Serializable data class RefreshRequest(val refresh: String)
@Serializable data class TokenPair(val access: String, val refresh: String)
@Serializable data class AccessToken(val access: String, val refresh: String? = null)

@Serializable
data class RegisterRequest(val username: String, val name: String, val email: String, val password: String)

@Serializable data class Group(val id: Int, val name: String, val description: String = "")
@Serializable data class GroupRequest(val name: String, val description: String)

@Serializable data class Member(val id: Int, val username: String, val name: String = "")
@Serializable data class UserSummary(val id: Int, val username: String, val name: String = "")

@Serializable
data class Expense(
    val id: Int,
    val name: String,
    val description: String = "",
    @Serializable(with = MoneySerializer::class) val amount: String,
    @SerialName("paid_by") val paidBy: List<Int> = emptyList(),
    @SerialName("split_on") val splitOn: List<Int> = emptyList(),
    @SerialName("group_id") val groupId: Int,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class ExpenseRequest(
    val name: String,
    val description: String,
    val amount: String,
    @SerialName("paid_by") val paidBy: List<Int>,
    @SerialName("split_on") val splitOn: List<Int>,
    @SerialName("group_id") val groupId: Int,
)

@Serializable
data class OwedLine(
    @SerialName("to_user") val toUser: String? = null,
    @SerialName("from_user") val fromUser: String? = null,
    @Serializable(with = MoneySerializer::class) val amount: String,
)

@Serializable
data class Settlements(
    @SerialName("You need to pay") val youOwe: List<OwedLine> = emptyList(),
    @SerialName("you will get") val owedToYou: List<OwedLine> = emptyList(),
)

@Serializable
data class Invite(
    val id: Int,
    val group: Int,
    val inviter: Int,
    val invitee: Int,
    val status: String,
)

@Serializable data class InviteRequest(val group: Int, val invitee: Int)

@Serializable
data class AppNotification(
    val id: Int,
    val message: String,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("read_at") val readAt: String? = null,
)
