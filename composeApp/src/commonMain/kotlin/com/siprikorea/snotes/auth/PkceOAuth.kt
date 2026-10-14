package com.siprikorea.snotes.auth

import com.russhwolf.settings.Settings
import com.siprikorea.snotes.config.OAuthConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * 설치형 앱용 OAuth 2.0 Authorization Code + PKCE (RFC 7636 / RFC 8252).
 * 데스크톱(루프백 리디렉션)과 iOS(ASWebAuthenticationSession)가 공유한다.
 * refresh token 은 [secureSettings] 에 저장한다(iOS 는 Keychain).
 */
class PkceOAuth(
    private val clientId: String,
    private val clientSecret: String?,
    private val http: HttpClient,
    private val secureSettings: Settings,
    private val randomBytes: (Int) -> ByteArray,
    /** 리디렉션 URI 를 정하고, 브라우저로 [authUrl] 을 열어 돌아온 콜백 URL 을 돌려준다. */
    private val browser: PkceBrowser,
) : GoogleAuth {

    interface PkceBrowser {
        suspend fun redirectUri(): String
        suspend fun authorize(authUrl: String, redirectUri: String): String
    }

    @Serializable
    private data class TokenResponse(
        val access_token: String,
        val expires_in: Long = 3600,
        val refresh_token: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var accessToken: String? = null
    private var expiresAt: Instant = Instant.DISTANT_PAST

    override val unavailableReason: String? =
        if (OAuthConfig.isConfigured(clientId)) null else OAuthConfig.NOT_CONFIGURED

    override suspend fun hasSession() = refreshToken() != null

    override suspend fun signIn() {
        unavailableReason?.let { throw NotSignedInException(it) }
        val verifier = b64(randomBytes(32))
        val challenge = b64(Sha256.digest(verifier.encodeToByteArray()))
        val state = b64(randomBytes(16))
        val redirect = browser.redirectUri()
        val authUrl = URLBuilder("https://accounts.google.com/o/oauth2/v2/auth").apply {
            parameters.append("client_id", clientId)
            parameters.append("redirect_uri", redirect)
            parameters.append("response_type", "code")
            parameters.append("scope", OAuthConfig.DRIVE_SCOPE)
            parameters.append("code_challenge", challenge)
            parameters.append("code_challenge_method", "S256")
            parameters.append("state", state)
            parameters.append("access_type", "offline")
            parameters.append("prompt", "consent")
        }.buildString()

        val callback = Url(browser.authorize(authUrl, redirect))
        callback.parameters["error"]?.let { throw NotSignedInException("로그인이 취소되었습니다 ($it)") }
        if (callback.parameters["state"] != state) throw NotSignedInException("잘못된 로그인 응답(state 불일치)")
        val code = callback.parameters["code"] ?: throw NotSignedInException("인증 코드가 없습니다")

        token(
            Parameters.build {
                append("grant_type", "authorization_code")
                append("code", code)
                append("code_verifier", verifier)
                append("redirect_uri", redirect)
            },
        )
    }

    override suspend fun accessToken(): String = mutex.withLock {
        accessToken?.takeIf { Clock.System.now() < expiresAt - 60.seconds }?.let { return it }
        val refresh = refreshToken() ?: throw NotSignedInException()
        token(
            Parameters.build {
                append("grant_type", "refresh_token")
                append("refresh_token", refresh)
            },
        )
    }

    override suspend fun invalidateToken() {
        accessToken = null
    }

    override suspend fun signOut() {
        val refresh = refreshToken()
        memoryRefresh = null
        runCatching { secureSettings.remove(REFRESH_KEY) }
        accessToken = null
        if (refresh != null) runCatching {
            http.submitForm("https://oauth2.googleapis.com/revoke", Parameters.build { append("token", refresh) })
        }
    }

    private suspend fun token(params: Parameters): String {
        val res = http.submitForm(
            "https://oauth2.googleapis.com/token",
            Parameters.build {
                appendAll(params)
                append("client_id", clientId)
                clientSecret?.let { append("client_secret", it) }
            },
        )
        val body = res.bodyAsText()
        if (!res.status.isSuccess()) {
            if (body.contains("invalid_grant")) { memoryRefresh = null; runCatching { secureSettings.remove(REFRESH_KEY) } } // 권한 철회됨
            throw NotSignedInException("토큰 요청 실패: ${body.take(200)}")
        }
        val t = json.decodeFromString<TokenResponse>(body)
        t.refresh_token?.let { rt ->
            // 저장에 실패해도(예: 서명되지 않은 iOS 빌드의 Keychain) 이번 실행 동안은 동작하도록 메모리에도 둔다.
            memoryRefresh = rt
            runCatching { secureSettings.putString(REFRESH_KEY, rt) }
        }
        accessToken = t.access_token
        expiresAt = Clock.System.now() + t.expires_in.seconds
        return t.access_token
    }

    private var memoryRefresh: String? = null

    private fun refreshToken(): String? =
        memoryRefresh ?: runCatching { secureSettings.getStringOrNull(REFRESH_KEY) }.getOrNull()

    private fun b64(bytes: ByteArray) = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)

    companion object {
        private const val REFRESH_KEY = "google_refresh_token"
    }
}
