package com.siprikorea.snotes

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import com.siprikorea.snotes.store.FileLocalStore
import io.ktor.client.HttpClient
import kotlinx.io.files.Path

/** 프로세스당 하나의 앱 그래프(화면 회전 등으로 Activity 가 다시 만들어져도 유지). */
object AndroidAppGraph {
    private var graph: AppGraph? = null
    private var auth: AndroidGoogleAuth? = null

    fun get(context: Context): Pair<AppGraph, AndroidGoogleAuth> {
        val a = auth ?: AndroidGoogleAuth(context).also { auth = it }
        val g = graph ?: AppGraph(
            store = FileLocalStore(Path(context.filesDir.absolutePath, "snotes")),
            auth = a,
            http = HttpClient(),
            settings = SharedPreferencesSettings(context.getSharedPreferences("snotes", Context.MODE_PRIVATE)),
        ).also { graph = it }
        return g to a
    }
}
