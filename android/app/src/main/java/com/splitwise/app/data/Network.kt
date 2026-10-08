package com.splitwise.app.data

import com.splitwise.app.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

val AppJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

private fun retrofit(client: OkHttpClient) = Retrofit.Builder()
    .baseUrl(BuildConfig.API_BASE_URL)
    .client(client)
    .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
    .build()

private fun baseClient() = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .apply {
        if (BuildConfig.DEBUG) {
            addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
    }

class AuthInterceptor(private val tokens: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokens.accessTokenBlocking() ?: return chain.proceed(chain.request())
        return chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $token").build())
    }
}

/** On 401, trades the refresh token for a new access token and retries once. */
class TokenAuthenticator(private val tokens: TokenStore, private val authApi: AuthApi) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("X-Retried") != null) return null
        synchronized(this) {
            val sent = response.request.header("Authorization")?.removePrefix("Bearer ")
            val current = tokens.accessTokenBlocking()
            // Another call already refreshed while we waited for the lock.
            if (current != null && current != sent) return retry(response, current)

            val refresh = tokens.refreshTokenBlocking() ?: return fail()
            val fresh = runCatching {
                kotlinx.coroutines.runBlocking { authApi.refresh(RefreshRequest(refresh)) }
            }.getOrNull() ?: return fail()
            tokens.saveBlocking(fresh.access, fresh.refresh)
            return retry(response, fresh.access)
        }
    }

    private fun retry(response: Response, token: String) = response.request.newBuilder()
        .header("Authorization", "Bearer $token").header("X-Retried", "1").build()

    private fun fail(): Request? {
        tokens.clearBlocking() // session expired: the UI observes this and returns to login
        return null
    }
}

class ApiClients(tokens: TokenStore) {
    val auth: AuthApi = retrofit(baseClient().build()).create(AuthApi::class.java)
    val api: SplitwiseApi = retrofit(
        baseClient()
            .addInterceptor(AuthInterceptor(tokens))
            .authenticator(TokenAuthenticator(tokens, auth))
            .build()
    ).create(SplitwiseApi::class.java)
}
