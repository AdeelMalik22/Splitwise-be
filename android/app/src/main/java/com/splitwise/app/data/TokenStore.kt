package com.splitwise.app.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val Context.dataStore by preferencesDataStore(name = "session")

/** Persists the JWT pair and the signed-in user's id. */
class TokenStore(private val context: Context) {
    private val accessKey = stringPreferencesKey("access")
    private val refreshKey = stringPreferencesKey("refresh")
    private val userIdKey = intPreferencesKey("user_id")

    /** null until the first read completes is avoided: this emits the stored state immediately. */
    private val alertsKey = booleanPreferencesKey("alerts_on")
    /** Show the unread badge on the Activity tab. A device setting like dark mode. */
    val alertsOn: Flow<Boolean> = context.dataStore.data.map { it[alertsKey] ?: true }
    suspend fun setAlertsOn(on: Boolean) { context.dataStore.edit { it[alertsKey] = on } }

    private val darkKey = booleanPreferencesKey("dark_mode")
    /** null = follow the system theme. Survives sign-out (it's a device setting). */
    val darkMode: Flow<Boolean?> = context.dataStore.data.map { it[darkKey] }
    suspend fun setDarkMode(dark: Boolean) { context.dataStore.edit { it[darkKey] = dark } }

    val loggedIn: Flow<Boolean> = context.dataStore.data.map { it[accessKey] != null }
    val userId: Flow<Int?> = context.dataStore.data.map { it[userIdKey] }

    // OkHttp interceptors run on background threads, so blocking reads are acceptable there.
    fun accessTokenBlocking(): String? = runBlocking { context.dataStore.data.first()[accessKey] }
    fun refreshTokenBlocking(): String? = runBlocking { context.dataStore.data.first()[refreshKey] }

    suspend fun save(access: String, refresh: String?) {
        context.dataStore.edit { prefs ->
            prefs[accessKey] = access
            if (refresh != null) prefs[refreshKey] = refresh
            jwtUserId(access)?.let { prefs[userIdKey] = it }
        }
    }

    fun saveBlocking(access: String, refresh: String?) = runBlocking { save(access, refresh) }

    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(accessKey); prefs.remove(refreshKey); prefs.remove(userIdKey)
        }
    }

    fun clearBlocking() = runBlocking { clear() }

    private fun jwtUserId(token: String): Int? = runCatching {
        val payload = token.split('.')[1]
        val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        Json.parseToJsonElement(json).jsonObject["user_id"]?.jsonPrimitive?.int
    }.getOrNull()
}
