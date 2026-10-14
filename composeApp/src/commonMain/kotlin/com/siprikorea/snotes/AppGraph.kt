package com.siprikorea.snotes

import com.russhwolf.settings.Settings
import com.siprikorea.snotes.auth.GoogleAuth
import com.siprikorea.snotes.store.LocalStore
import com.siprikorea.snotes.store.PageRepository
import com.siprikorea.snotes.sync.SyncController
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.plus

/** 플랫폼마다 저장소와 로그인 구현만 다르게 넣어 만든다. */
class AppGraph(
    store: LocalStore,
    auth: GoogleAuth,
    http: HttpClient,
    val settings: Settings = Settings(),
    val scope: CoroutineScope = MainScope() + CoroutineExceptionHandler { _, e ->
        // 백그라운드 작업(동기화/저장)의 예외가 앱을 종료시키지 않도록 한다.
        println("S Notes: ${e.message}")
    },
) {
    val repo = PageRepository(store)
    val sync = SyncController(repo, store, auth, http, settings, scope)
}
