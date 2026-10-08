package com.splitwise.app.data

import retrofit2.http.Body
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

    @GET("usersgroup/{groupId}/users/") suspend fun members(@Path("groupId") groupId: Int): List<Member>

    // The list endpoint returns every expense visible to the user as a plain array.
    @GET("expense/") suspend fun expenses(): List<Expense>
    @POST("expense/") suspend fun createExpense(@Body body: ExpenseRequest): Expense
    @GET("expense/{groupId}/settlements/") suspend fun settlements(@Path("groupId") groupId: Int): Settlements

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
