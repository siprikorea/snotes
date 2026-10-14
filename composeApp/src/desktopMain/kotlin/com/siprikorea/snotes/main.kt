package com.siprikorea.snotes

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.russhwolf.settings.PreferencesSettings
import com.siprikorea.snotes.store.FileLocalStore
import io.ktor.client.HttpClient
import kotlinx.io.files.Path
import java.io.File
import java.util.prefs.Preferences

fun main() {
    val http = HttpClient()
    val graph = AppGraph(
        store = FileLocalStore(Path(appDataDir().absolutePath)),
        auth = desktopGoogleAuth(http),
        http = http,
        settings = PreferencesSettings(Preferences.userRoot().node("com/siprikorea/snotes")),
    )
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "S Notes",
            state = rememberWindowState(width = 1200.dp, height = 800.dp),
        ) {
            App(graph)
        }
    }
}

/** OS 관례에 맞는 앱 데이터 폴더. 개발/테스트용으로 SNOTES_DATA_DIR 환경 변수로 바꿀 수 있다. */
private fun appDataDir(): File {
    System.getenv("SNOTES_DATA_DIR")?.let { return File(it).also { d -> d.mkdirs() } }
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val home = System.getProperty("user.home")
    val dir = when {
        os.contains("mac") -> File(home, "Library/Application Support/SNotes")
        os.contains("win") -> File(System.getenv("APPDATA") ?: home, "SNotes")
        else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "snotes")
    }
    return dir.also { it.mkdirs() }
}
