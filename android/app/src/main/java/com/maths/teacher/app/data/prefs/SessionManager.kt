package com.maths.teacher.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

object SessionKeys {
    val TOKEN = stringPreferencesKey("token")
    val USER_ID = stringPreferencesKey("user_id")
    val FIRST_NAME = stringPreferencesKey("first_name")
    val LAST_NAME = stringPreferencesKey("last_name")
    val EMAIL = stringPreferencesKey("email")
}

class SessionManager(private val context: Context) {

    /** In-memory token for OkHttp interceptor; updated on save/clear/loadFromStore. */
    @Volatile
    var currentToken: String? = null
        private set

    val token: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[SessionKeys.TOKEN]?.takeIf { it.isNotBlank() }
    }

    val userId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[SessionKeys.USER_ID]?.toLongOrNull()
    }

    val displayName: Flow<String?> = context.dataStore.data.map { prefs ->
        val first = prefs[SessionKeys.FIRST_NAME] ?: ""
        val last = prefs[SessionKeys.LAST_NAME] ?: ""
        when {
            first.isNotBlank() && last.isNotBlank() -> "$first $last"
            first.isNotBlank() -> first
            last.isNotBlank() -> last
            else -> prefs[SessionKeys.EMAIL] ?: null
        }
    }

    suspend fun saveSession(
        token: String,
        userId: Long,
        firstName: String,
        lastName: String,
        email: String
    ) {
        currentToken = token
        context.dataStore.edit { prefs ->
            prefs[SessionKeys.TOKEN] = token
            prefs[SessionKeys.USER_ID] = userId.toString()
            prefs[SessionKeys.FIRST_NAME] = firstName
            prefs[SessionKeys.LAST_NAME] = lastName
            prefs[SessionKeys.EMAIL] = email
        }
    }

    suspend fun clearSession() {
        currentToken = null
        context.dataStore.edit { it.clear() }
    }

    /** Load token from DataStore into memory (call at app start before making API calls). */
    suspend fun loadFromStore() {
        currentToken = context.dataStore.data.first()[SessionKeys.TOKEN]?.takeIf { it.isNotBlank() }
    }
}
