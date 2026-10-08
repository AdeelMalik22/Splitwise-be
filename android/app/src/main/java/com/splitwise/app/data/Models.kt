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

object NullableMoneySerializer : KSerializer<String?> {
    override val descriptor = PrimitiveSerialDescriptor("NullableMoney", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String? =
        ((decoder as JsonDecoder).decodeJsonElement() as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content
    override fun serialize(encoder: Encoder, value: String?) = encoder.encodeString(value.orEmpty())
}

@Serializable data class Page<T>(val results: List<T> = emptyList())

@Serializable data class LoginRequest(val username: String, val password: String)
@Serializable data class RefreshRequest(val refresh: String)
@Serializable data class TokenPair(val access: String, val refresh: String)
@Serializable data class AccessToken(val access: String, val refresh: String? = null)

@Serializable
data class RegisterRequest(val username: String, val name: String, val email: String, val password: String)

@Serializable
data class Group(
    val id: Int,
    val name: String,
    val description: String = "",
    val icon: String = "",
    @SerialName("created_by") val createdBy: Int? = null,
)
@Serializable data class GroupRequest(val name: String, val description: String, val icon: String = "")

@Serializable data class Member(val id: Int, val username: String, val name: String = "")
@Serializable data class UserSummary(val id: Int, val username: String, val name: String = "")

@Serializable
data class SplitDetail(
    @SerialName("user_id") val userId: Int,
    @Serializable(with = NullableMoneySerializer::class) val amount: String? = null,
    @Serializable(with = NullableMoneySerializer::class) val percentage: String? = null,
)

@Serializable
data class Expense(
    val id: Int,
    val name: String,
    val description: String = "",
    @Serializable(with = MoneySerializer::class) val amount: String,
    @SerialName("paid_by") val paidBy: List<Int> = emptyList(),
    @SerialName("split_on") val splitOn: List<Int> = emptyList(),
    @SerialName("split_details") val splitDetails: List<SplitDetail> = emptyList(),
    @SerialName("group_id") val groupId: Int,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable data class SplitShare(@SerialName("user_id") val userId: Int, val amount: String? = null, val percentage: String? = null)

/** Either [splitOn] (equal split) or [splitDetails] (exact amounts / percentages) is sent. */
@Serializable
data class ExpenseRequest(
    val name: String,
    val description: String,
    val amount: String,
    @SerialName("paid_by") val paidBy: List<Int>,
    @SerialName("split_on") val splitOn: List<Int>? = null,
    @SerialName("split_details") val splitDetails: List<SplitShare>? = null,
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
    @SerialName("group_name") val groupName: String = "",
    val inviter: Int,
    @SerialName("inviter_username") val inviterUsername: String = "",
    @SerialName("inviter_name") val inviterName: String = "",
    val invitee: Int,
    @SerialName("invitee_username") val inviteeUsername: String = "",
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

@Serializable data class Profile(val id: Int, val username: String, val name: String = "", val email: String = "")

@Serializable
data class ActivityItem(
    val id: Int,
    val action: String,
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: Int? = null,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class Payment(
    val id: Int,
    val group: Int,
    @SerialName("group_name") val groupName: String = "",
    val payer: Int,
    @SerialName("payer_username") val payerUsername: String = "",
    val payee: Int,
    @SerialName("payee_username") val payeeUsername: String = "",
    @Serializable(with = MoneySerializer::class) val amount: String,
    val status: String,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable data class PaymentRequest(val group: Int, val payee: Int, val amount: String)
@Serializable data class Membership(val id: Int, @SerialName("group_id") val groupId: Int)
@Serializable data class ProfileUpdate(val name: String, val email: String)
@Serializable data class ChangePasswordRequest(@SerialName("old_password") val oldPassword: String, @SerialName("new_password") val newPassword: String)
@Serializable data class DeleteAccountRequest(val password: String)
