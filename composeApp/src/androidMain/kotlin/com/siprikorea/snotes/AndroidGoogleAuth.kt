package com.siprikorea.snotes

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.siprikorea.snotes.auth.GoogleAuth
import com.siprikorea.snotes.auth.NotSignedInException
import com.siprikorea.snotes.config.OAuthConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android: Google Play 서비스의 AuthorizationClient 로 drive.file 권한을 요청한다.
 * 클라이언트 ID 는 필요 없고, Cloud Console 에 패키지 이름 + 서명 SHA-1 로 Android 클라이언트를 등록해야 한다.
 *
 * [register] 는 Activity 의 onCreate 에서(STARTED 이전) 호출해야 한다.
 */
class AndroidGoogleAuth(context: Context) : GoogleAuth {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("snotes_auth", Context.MODE_PRIVATE)
    private var activity: ComponentActivity? = null
    private var launcher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var pending: CompletableDeferred<AuthorizationResult>? = null
    private var token: String? = null

    fun register(activity: ComponentActivity) {
        this.activity = activity
        launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val deferred = pending ?: return@registerForActivityResult
            pending = null
            if (result.resultCode != Activity.RESULT_OK || result.data == null) {
                deferred.completeExceptionally(NotSignedInException("로그인이 취소되었습니다"))
            } else {
                runCatching { Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(result.data) }
                    .onSuccess { deferred.complete(it) }
                    .onFailure { deferred.completeExceptionally(it) }
            }
        }
    }

    override val unavailableReason: String? = null

    override suspend fun hasSession() = prefs.getBoolean(SIGNED_IN, false)

    override suspend fun signIn() {
        token = authorize(interactive = true)
        prefs.edit().putBoolean(SIGNED_IN, true).apply()
    }

    override suspend fun accessToken(): String {
        token?.let { return it }
        if (!hasSession()) throw NotSignedInException()
        return authorize(interactive = false).also { token = it }
    }

    override suspend fun invalidateToken() {
        token = null
    }

    override suspend fun signOut() {
        token = null
        prefs.edit().remove(SIGNED_IN).apply()
    }

    private suspend fun authorize(interactive: Boolean): String {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(OAuthConfig.DRIVE_SCOPE)))
            .build()
        var result = Identity.getAuthorizationClient(appContext).authorize(request).await()
        if (result.hasResolution()) {
            if (!interactive) throw NotSignedInException("Google 계정 권한을 다시 확인해야 합니다. 설정에서 로그인하세요.")
            val l = launcher ?: throw NotSignedInException("화면이 준비되지 않았습니다")
            val deferred = CompletableDeferred<AuthorizationResult>().also { pending = it }
            l.launch(IntentSenderRequest.Builder(result.pendingIntent!!.intentSender).build())
            result = deferred.await()
        }
        return result.accessToken ?: throw NotSignedInException("액세스 토큰을 받지 못했습니다")
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }

    private companion object {
        const val SIGNED_IN = "signed_in"
    }
}
