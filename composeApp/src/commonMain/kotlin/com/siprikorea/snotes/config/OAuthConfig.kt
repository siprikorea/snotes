package com.siprikorea.snotes.config

/**
 * Google Cloud Console 에서 만든 OAuth 클라이언트 ID. README 의 "Google 동기화 설정" 참고.
 *
 * Android 는 클라이언트 ID 를 코드에 넣지 않는다(패키지 이름 + 서명 SHA-1 로 등록한 Android 클라이언트를
 * Google Play 서비스가 자동으로 찾는다).
 */
object OAuthConfig {
    /** "데스크톱 앱" 유형 클라이언트. 설치형 앱의 client secret 은 비밀이 아니다(Google 문서 참고). */
    const val DESKTOP_CLIENT_ID = "YOUR_DESKTOP_CLIENT_ID.apps.googleusercontent.com"
    const val DESKTOP_CLIENT_SECRET = "YOUR_DESKTOP_CLIENT_SECRET"

    /** "iOS" 유형 클라이언트. */
    const val IOS_CLIENT_ID = "YOUR_IOS_CLIENT_ID.apps.googleusercontent.com"

    /** "웹 애플리케이션" 유형 클라이언트. 승인된 JavaScript 원본에 웹 앱 주소를 등록해야 한다. */
    const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"

    /** 앱이 만든 파일에만 접근하는 최소 범위. */
    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"

    fun isConfigured(clientId: String) = !clientId.startsWith("YOUR_")

    const val NOT_CONFIGURED = "OAuth 클라이언트가 설정되지 않았습니다. config/OAuthConfig.kt 에 클라이언트 ID 를 입력하세요."
}
