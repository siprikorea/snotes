package com.siprikorea.snotes.auth

/** Drive 접근 토큰을 얻는 플랫폼별 Google 로그인. */
interface GoogleAuth {
    /** 로그인할 수 없는 이유(예: 클라이언트 ID 미설정). null 이면 사용 가능. */
    val unavailableReason: String?

    /** 사용자 개입 없이 토큰을 얻을 수 있을 것 같은지(앱 시작 시 자동 동기화 여부). */
    suspend fun hasSession(): Boolean

    /** 로그인/동의 화면을 띄운다. */
    suspend fun signIn()

    /** 유효한 액세스 토큰. 조용히 얻을 수 없으면 [NotSignedInException]. */
    suspend fun accessToken(): String

    /** 서버가 401 을 돌려줬을 때 캐시된 토큰을 버린다. */
    suspend fun invalidateToken()

    suspend fun signOut()
}

class NotSignedInException(message: String = "Google 로그인이 필요합니다.") : Exception(message)

/** 테스트/미지원 환경용. */
class UnavailableAuth(override val unavailableReason: String) : GoogleAuth {
    override suspend fun hasSession() = false
    override suspend fun signIn() = throw NotSignedInException(unavailableReason)
    override suspend fun accessToken() = throw NotSignedInException(unavailableReason)
    override suspend fun invalidateToken() {}
    override suspend fun signOut() {}
}
