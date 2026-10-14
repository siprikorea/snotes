package com.siprikorea.snotes

import com.russhwolf.settings.PreferencesSettings
import com.siprikorea.snotes.auth.GoogleAuth
import com.siprikorea.snotes.auth.PkceOAuth
import com.siprikorea.snotes.config.OAuthConfig
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.awt.Desktop
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.security.SecureRandom
import java.util.prefs.Preferences
import kotlin.time.Duration.Companion.minutes

/**
 * 데스크톱: 시스템 브라우저로 Google 로그인 후 127.0.0.1 루프백 주소로 인증 코드를 받는다(RFC 8252).
 */
fun desktopGoogleAuth(http: HttpClient): GoogleAuth = PkceOAuth(
    clientId = OAuthConfig.DESKTOP_CLIENT_ID,
    clientSecret = OAuthConfig.DESKTOP_CLIENT_SECRET,
    http = http,
    secureSettings = PreferencesSettings(Preferences.userRoot().node("com/siprikorea/snotes/auth")),
    randomBytes = { n -> ByteArray(n).also { SecureRandom().nextBytes(it) } },
    browser = LoopbackBrowser(),
)

private class LoopbackBrowser : PkceOAuth.PkceBrowser {
    private var server: HttpServer? = null
    private var result = CompletableDeferred<String>()

    override suspend fun redirectUri(): String {
        server?.stop(0)
        result = CompletableDeferred()
        val s = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        s.createContext("/") { ex ->
            val uri = "http://127.0.0.1:${s.address.port}${ex.requestURI}"
            val body = """
                <!doctype html><meta charset="utf-8"><title>S Notes</title>
                <body style="font-family:sans-serif;text-align:center;padding-top:80px">
                <h2>로그인되었습니다</h2><p>이 창을 닫고 S Notes 로 돌아가세요.</p></body>
            """.trimIndent().toByteArray()
            ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
            if (ex.requestURI.query?.contains("code=") == true || ex.requestURI.query?.contains("error=") == true) {
                result.complete(uri)
            }
        }
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}"
    }

    override suspend fun authorize(authUrl: String, redirectUri: String): String {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(authUrl))
            } else {
                Runtime.getRuntime().exec(arrayOf("xdg-open", authUrl))
            }
            return withTimeout(5.minutes) { result.await() }
        } finally {
            server?.stop(1)
            server = null
        }
    }
}
