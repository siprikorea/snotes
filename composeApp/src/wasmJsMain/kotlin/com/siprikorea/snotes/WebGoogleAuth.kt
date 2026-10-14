package com.siprikorea.snotes

import com.siprikorea.snotes.auth.GoogleAuth
import com.siprikorea.snotes.auth.NotSignedInException
import com.siprikorea.snotes.config.OAuthConfig
import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Suppress("UNUSED_PARAMETER")
private fun gisRequestToken(clientId: String, scope: String, prompt: String, callback: (JsString?, Int, JsString?) -> Unit): Unit = js(
    """
    {
        if (!(window.google && google.accounts && google.accounts.oauth2)) { callback(null, 0, 'Google 로그인 스크립트를 불러오지 못했습니다'); return; }
        const client = google.accounts.oauth2.initTokenClient({
            client_id: clientId,
            scope: scope,
            callback: (r) => callback(r.access_token || null, Number(r.expires_in || 0), r.error || null),
            error_callback: (e) => callback(null, 0, (e && (e.message || e.type)) || 'popup_error'),
        });
        client.requestAccessToken({ prompt: prompt });
    }
    """,
)

@Suppress("UNUSED_PARAMETER")
private fun gisRevoke(token: String): Unit = js("{ if (window.google && google.accounts) google.accounts.oauth2.revoke(token, () => {}); }")

/**
 * 웹: Google Identity Services 토큰 클라이언트(팝업). 토큰은 1시간 유효하며 세션 저장소에 둔다.
 * 팝업은 사용자 동작으로만 열 수 있으므로 만료되면 설정에서 다시 로그인해야 한다.
 */
class WebGoogleAuth : GoogleAuth {
    private val clientId = OAuthConfig.WEB_CLIENT_ID

    override val unavailableReason: String? =
        if (OAuthConfig.isConfigured(clientId)) null else OAuthConfig.NOT_CONFIGURED

    private fun cached(): String? {
        val t = window.sessionStorage.getItem(TOKEN) ?: return null
        val exp = window.sessionStorage.getItem(EXPIRES)?.toLongOrNull() ?: return null
        return t.takeIf { Clock.System.now() < Instant.fromEpochMilliseconds(exp) - 60.seconds }
    }

    override suspend fun hasSession() = cached() != null

    override suspend fun signIn() {
        unavailableReason?.let { throw NotSignedInException(it) }
        val result = CompletableDeferred<String>()
        gisRequestToken(clientId, OAuthConfig.DRIVE_SCOPE, "") { token, expiresIn, error ->
            if (token != null) {
                val exp = Clock.System.now() + expiresIn.seconds
                window.sessionStorage.setItem(TOKEN, token.toString())
                window.sessionStorage.setItem(EXPIRES, exp.toEpochMilliseconds().toString())
                result.complete(token.toString())
            } else {
                result.completeExceptionally(NotSignedInException("로그인 실패: ${error ?: "알 수 없음"}"))
            }
        }
        result.await()
    }

    override suspend fun accessToken(): String = cached() ?: throw NotSignedInException("로그인이 만료되었습니다. 설정에서 다시 로그인하세요.")

    override suspend fun invalidateToken() {
        window.sessionStorage.removeItem(TOKEN)
    }

    override suspend fun signOut() {
        cached()?.let(::gisRevoke)
        window.sessionStorage.removeItem(TOKEN)
        window.sessionStorage.removeItem(EXPIRES)
    }

    private companion object {
        const val TOKEN = "snotes_token"
        const val EXPIRES = "snotes_token_expires"
    }
}
