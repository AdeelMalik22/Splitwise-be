package com.splitwise.app.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.PATCH
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Unauthenticated endpoints, also used by the token refresher (no interceptor loops). */
interface AuthApi {
    @POST("login/") suspend fun login(@Body body: LoginRequest): TokenPair
    @POST("login/refresh/") suspend fun refresh(@Body body: RefreshRequest): AccessToken
    @POST("users/register/") suspend fun register(@Body body: RegisterRequest): UserSummary
}

interface SplitwiseApi {
    @GET("groups/") suspend fun groups(): Page<Group>
    @POST("groups/") suspend fun createGroup(@Body body: GroupRequest): Group
    @PATCH("groups/{id}/") suspend fun updateGroup(@Path("id") id: Int, @Body body: GroupRequest): Group
    @DELETE("groups/{id}/") suspend fun deleteGroup(@Path("id") id: Int)
    @DELETE("groups/{id}/members/{userId}/") suspend fun removeMember(@Path("id") id: Int, @Path("userId") userId: Int)

    @GET("usersgroup/{groupId}/users/") suspend fun members(@Path("groupId") groupId: Int): List<Member>

    // The list endpoint returns every expense visible to the user as a plain array.
    @GET("expense/") suspend fun expenses(): List<Expense>
    @POST("expense/") suspend fun createExpense(@Body body: ExpenseRequest): Expense
    @GET("expense/{id}/") suspend fun expense(@Path("id") id: Int): Expense
    @PATCH("expense/{id}/") suspend fun updateExpense(@Path("id") id: Int, @Body body: ExpenseRequest): Expense
    @GET("expense/{groupId}/settlements/") suspend fun settlements(@Path("groupId") groupId: Int): Settlements

    @DELETE("expense/{id}/") suspend fun deleteExpense(@Path("id") id: Int)

    @GET("usersgroup/") suspend fun memberships(): Page<Membership>
    @DELETE("usersgroup/{id}/") suspend fun leaveGroup(@Path("id") id: Int)

    @GET("payments/") suspend fun payments(): Page<Payment>
    @POST("payments/") suspend fun createPayment(@Body body: PaymentRequest): Payment
    @POST("payments/{id}/confirm/") suspend fun confirmPayment(@Path("id") id: Int): Payment
    @DELETE("payments/{id}/") suspend fun cancelPayment(@Path("id") id: Int)

    @PATCH("users/{id}/") suspend fun updateProfile(@Path("id") id: Int, @Body body: ProfileUpdate): Profile
    @POST("users/change_password/") suspend fun changePassword(@Body body: ChangePasswordRequest)
    @POST("users/delete_account/") suspend fun deleteAccount(@Body body: DeleteAccountRequest)

    @GET("users/search/") suspend fun searchUsers(@Query("q") query: String): List<UserSummary>

    @GET("invites/") suspend fun invites(): Page<Invite>
    @POST("invites/") suspend fun invite(@Body body: InviteRequest): Invite
    @POST("invites/{id}/accept/") suspend fun acceptInvite(@Path("id") id: Int): Invite
    @POST("invites/{id}/decline/") suspend fun declineInvite(@Path("id") id: Int): Invite

    @GET("users/") suspend fun profile(): Page<Profile>
    @GET("activity/") suspend fun activity(): Page<ActivityItem>

    @GET("notifications/") suspend fun notifications(): Page<AppNotification>
    @POST("notifications/{id}/mark_read/") suspend fun markRead(@Path("id") id: Int): AppNotification
}
