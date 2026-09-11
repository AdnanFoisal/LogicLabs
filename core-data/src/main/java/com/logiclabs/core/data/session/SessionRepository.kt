package com.logiclabs.core.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What kind of thing was on the bench when the user last left it. */
enum class SessionKind { LAB, PROJECT, SANDBOX }

/**
 * The pointer behind the Home screen's CONTINUE card: enough to rebuild the exact
 * bench, nothing more. The board itself is rebuilt deterministically — a LAB replays
 * its preset, a PROJECT re-reads its file, a SANDBOX reloads the autosave draft.
 */
data class Session(
    val kind: SessionKind,
    val id: String?,
    val savedAtMillis: Long
)

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "logiclabs_session"
)

/**
 * Records what the user was last doing. One row, overwritten on every bench
 * transition; the Home screen reads it once per composition.
 */
class SessionRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val lastSession: Flow<Session?> = appContext.sessionDataStore.data.map { p ->
        val kind = p[KEY_KIND]?.let { runCatching { SessionKind.valueOf(it) }.getOrNull() }
            ?: return@map null
        Session(
            kind = kind,
            id = p[KEY_ID],
            savedAtMillis = p[KEY_AT] ?: 0L
        )
    }

    /** Blocking one-shot read for the bench's continue-resolution, which runs once. */
    suspend fun current(): Session? = lastSession.first()

    fun recordLab(labId: String) = record(SessionKind.LAB, labId)

    fun recordProject(projectId: String) = record(SessionKind.PROJECT, projectId)

    fun recordSandbox() = record(SessionKind.SANDBOX, null)

    private fun record(kind: SessionKind, id: String?) {
        scope.launch {
            appContext.sessionDataStore.edit { p ->
                p[KEY_KIND] = kind.name
                if (id != null) p[KEY_ID] = id else p.remove(KEY_ID)
                p[KEY_AT] = System.currentTimeMillis()
            }
        }
    }

    private companion object {
        val KEY_KIND = stringPreferencesKey("kind")
        val KEY_ID = stringPreferencesKey("id")
        val KEY_AT = longPreferencesKey("saved_at")
    }
}
