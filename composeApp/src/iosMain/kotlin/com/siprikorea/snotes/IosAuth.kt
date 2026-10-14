package com.siprikorea.snotes

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.siprikorea.snotes.auth.GoogleAuth
import com.siprikorea.snotes.auth.NotSignedInException
import com.siprikorea.snotes.auth.PkceOAuth
import com.siprikorea.snotes.config.OAuthConfig
import io.ktor.client.HttpClient
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS: ASWebAuthenticationSession 으로 Google 로그인(PKCE). refresh token 은 Keychain 에 저장한다.
 * 리디렉션은 iOS 클라이언트 ID 를 뒤집은 스킴(com.googleusercontent.apps.XXX:/oauth2redirect)을 쓴다.
 */
@OptIn(ExperimentalSettingsImplementation::class, ExperimentalForeignApi::class)
fun iosGoogleAuth(http: HttpClient): GoogleAuth = PkceOAuth(
    clientId = OAuthConfig.IOS_CLIENT_ID,
    clientSecret = null,
    http = http,
    secureSettings = KeychainSettings(service = "com.siprikorea.snotes.auth"),
    randomBytes = { n ->
        ByteArray(n).also { bytes -> bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, n.toULong(), it.addressOf(0)) } }
    },
    browser = WebAuthBrowser(),
)

private class WebAuthBrowser : PkceOAuth.PkceBrowser {
    private val scheme: String =
        "com.googleusercontent.apps." + OAuthConfig.IOS_CLIENT_ID.removeSuffix(".apps.googleusercontent.com")

    // 세션이 진행되는 동안 참조를 유지해야 한다.
    private var session: ASWebAuthenticationSession? = null
    private val anchorProvider = AnchorProvider()

    override suspend fun redirectUri() = "$scheme:/oauth2redirect"

    override suspend fun authorize(authUrl: String, redirectUri: String): String = suspendCancellableCoroutine { cont ->
        val s = ASWebAuthenticationSession(
            uRL = NSURL(string = authUrl),
            callbackURLScheme = scheme,
        ) { url, error ->
            session = null
            when {
                url != null -> cont.resume(url.absoluteString ?: "")
                else -> cont.resumeWithException(NotSignedInException(error?.localizedDescription ?: "로그인이 취소되었습니다"))
            }
        }
        s.presentationContextProvider = anchorProvider
        s.prefersEphemeralWebBrowserSession = false
        session = s
        if (!s.start()) {
            session = null
            cont.resumeWithException(NotSignedInException("로그인 화면을 열 수 없습니다"))
        }
        cont.invokeOnCancellation { s.cancel() }
    }
}

private class AnchorProvider : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
        UIApplication.sharedApplication.keyWindow ?: UIWindow()
}
