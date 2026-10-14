package com.siprikorea.snotes

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.russhwolf.settings.PreferencesSettings
import com.siprikorea.snotes.auth.UnavailableAuth
import com.siprikorea.snotes.store.MemoryLocalStore
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * 화면을 PNG 로 렌더링한다(눈으로 레이아웃 확인용). SNOTES_SCREENSHOT_DIR 이 있을 때만 파일을 쓴다.
 */
class ScreenshotTest {
    private val outDir = System.getenv("SNOTES_SCREENSHOT_DIR")?.let(::File)

    private fun shot(name: String, width: Int, height: Int, interact: (ImageComposeScene) -> Unit = {}): AppGraph {
        val graph = AppGraph(
            store = MemoryLocalStore(),
            auth = UnavailableAuth("테스트"),
            http = HttpClient(),
            settings = PreferencesSettings(java.util.prefs.Preferences.userRoot().node("snotes-test-" + System.nanoTime())),
            scope = MainScope(),
        )
        ImageComposeScene(width, height, Density(2f), coroutineContext = Dispatchers.Unconfined) { App(graph) }.use { scene ->
            var t = 0L
            repeat(30) {
                scene.render(t)
                t += 50_000_000
                Thread.sleep(50)
                if (it == 10) interact(scene)
            }
            val img = scene.render(t)
            outDir?.let { dir ->
                dir.mkdirs()
                File(dir, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
        }
        return graph
    }

    private fun ImageComposeScene.click(x: Float, y: Float) {
        sendPointerEvent(PointerEventType.Press, Offset(x, y))
        render(0)
        sendPointerEvent(PointerEventType.Release, Offset(x, y))
        render(0)
    }

    /**
     * 본문 아래 빈 곳을 누르면 마지막 빈 문단에 포커스가 가고 편집 도구 모음이 나타난다(스크린샷으로 확인).
     * 오프스크린 장면에서는 키 입력이 플랫폼 IME 를 거치지 않아 글자 입력은 EditorStateTest 로 검증한다.
     */
    @Test
    fun clickBelowContentFocusesEditor() {
        val graph = shot("focus", 2400, 1600) { scene ->
            scene.click(1600f, 1560f)
        }
        val page = graph.repo.pages.value.values.single()
        kotlin.test.assertEquals("", page.blocks.last().text, "빈 문단을 재사용하고 새 블록을 만들지 않는다")
    }

    @Test
    fun desktopLayout() { shot("desktop", 2400, 1600) }

    @Test
    fun phoneLayout() { shot("phone", 780, 1600) }
}
