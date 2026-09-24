package com.example.munandydrive.data.remote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.munandydrive.data.model.LoggedInUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

class TokenManager(context: Context) {

    private val dataStore: DataStore<Preferences> = context.applicationContext.dataStore
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _sessionExpiredEvents = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sessionExpiredEvents: SharedFlow<Unit> = _sessionExpiredEvents.asSharedFlow()

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("jwt_token")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USER_NAME_KEY = stringPreferencesKey("user_name")
        private val USER_ROLE_KEY = stringPreferencesKey("user_role")
        private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
    }

    suspend fun saveSession(token: String, user: LoggedInUser) {
        dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
            prefs[USER_ID_KEY] = user.id.toString()
            prefs[USER_NAME_KEY] = user.name
            prefs[USER_ROLE_KEY] = user.role
            prefs[USER_EMAIL_KEY] = user.email
        }
    }

    suspend fun clearSession() {
        dataStore.edit { it.clear() }
    }

    suspend fun getStoredRole(): String? =
        dataStore.data.map { it[USER_ROLE_KEY] }.first()

    fun getTokenBlocking(): String? = runBlocking {
        dataStore.data.map { it[TOKEN_KEY] }.first()
    }

    suspend fun getStoredUser(): com.example.munandydrive.data.model.LoggedInUser? {
        val prefs = dataStore.data.first()
        val id    = prefs[USER_ID_KEY]?.toIntOrNull() ?: return null
        val name  = prefs[USER_NAME_KEY]              ?: return null
        val role  = prefs[USER_ROLE_KEY]              ?: return null
        val email = prefs[USER_EMAIL_KEY]             ?: return null
        return com.example.munandydrive.data.model.LoggedInUser(id, name, role, email)
    }

    // Called from OkHttp interceptor — must NOT use runBlocking (would deadlock on the I/O thread)
    fun notifySessionExpired() {
        ioScope.launch {
            clearSession()
            _sessionExpiredEvents.emit(Unit)
        }
    }
}
