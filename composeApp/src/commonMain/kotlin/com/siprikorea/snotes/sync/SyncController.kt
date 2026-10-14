package com.siprikorea.snotes.sync

import com.russhwolf.settings.Settings
import com.siprikorea.snotes.auth.GoogleAuth
import com.siprikorea.snotes.auth.NotSignedInException
import com.siprikorea.snotes.store.LocalStore
import com.siprikorea.snotes.store.PageRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

sealed interface SyncStatus {
    data object SignedOut : SyncStatus
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Done(val at: Instant, val report: SyncReport) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

/** 로그인 상태와 동기화 실행(수동/앱 시작/편집 후 30초)을 관리한다. */
class SyncController(
    private val repo: PageRepository,
    store: LocalStore,
    val auth: GoogleAuth,
    private val http: HttpClient,
    private val settings: Settings,
    private val scope: CoroutineScope,
) {
    private val engine = SyncEngine(repo, store)
    private val mutex = Mutex()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.SignedOut)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _account = MutableStateFlow(settings.getStringOrNull(ACCOUNT_KEY))
    val account: StateFlow<String?> = _account.asStateFlow()

    private val api = GoogleDriveApi(http, token = { auth.accessToken() }, onUnauthorized = { auth.invalidateToken() })

    fun start() {
        scope.launch {
            if (runCatching { auth.hasSession() }.getOrDefault(false)) {
                _status.value = SyncStatus.Idle
                syncNow()
            }
        }
        scope.launch {
            repo.localEdits.collectLatest {
                delay(30.seconds)
                if (_status.value !is SyncStatus.SignedOut) syncNow()
            }
        }
    }

    fun signIn() {
        scope.launch {
            try {
                auth.signIn()
                _status.value = SyncStatus.Idle
                runCatching { api.userEmail() }.getOrNull()?.let {
                    settings.putString(ACCOUNT_KEY, it)
                    _account.value = it
                }
                syncNow()
            } catch (e: Exception) {
                _status.value = SyncStatus.Failed(e.message ?: e.toString())
            }
        }
    }

    fun signOut() {
        scope.launch {
            auth.signOut()
            settings.remove(ACCOUNT_KEY)
            _account.value = null
            _status.value = SyncStatus.SignedOut
        }
    }

    fun requestSync() {
        scope.launch { syncNow() }
    }

    /** 앱이 다시 앞으로 올 때. 마지막 동기화 후 1분이 지났으면 동기화. */
    fun onResume() {
        val s = _status.value
        if (s is SyncStatus.Done && Clock.System.now() - s.at < 60.seconds) return
        if (s is SyncStatus.SignedOut || s is SyncStatus.Syncing) return
        requestSync()
    }

    private suspend fun syncNow() {
        if (!mutex.tryLock()) return
        try {
            _status.value = SyncStatus.Syncing
            val report = engine.sync(api)
            _status.value = SyncStatus.Done(Clock.System.now(), report)
        } catch (e: NotSignedInException) {
            _status.value = if (auth.hasSession()) SyncStatus.Failed(e.message ?: "") else SyncStatus.SignedOut
        } catch (e: Exception) {
            _status.value = SyncStatus.Failed(e.message ?: e.toString())
        } finally {
            mutex.unlock()
        }
    }

    private companion object {
        const val ACCOUNT_KEY = "google_account"
    }
}
