package com.siprikorea.snotes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.siprikorea.snotes.model.Block
import com.siprikorea.snotes.model.BlockType
import com.siprikorea.snotes.store.PageRepository
import com.siprikorea.snotes.ui.AppUiState
import com.siprikorea.snotes.ui.EmptyState
import com.siprikorea.snotes.ui.MarkdownDialog
import com.siprikorea.snotes.ui.MoveDialog
import com.siprikorea.snotes.ui.PageScreen
import com.siprikorea.snotes.ui.SearchDialog
import com.siprikorea.snotes.ui.SettingsDialog
import com.siprikorea.snotes.ui.Sidebar
import com.siprikorea.snotes.ui.TrashDialog
import com.siprikorea.snotes.ui.theme.SNotesTheme
import com.siprikorea.snotes.ui.theme.noteColors
import kotlinx.coroutines.launch

@Composable
fun App(graph: AppGraph) {
    SNotesTheme {
        var loaded by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            graph.repo.load()
            if (graph.repo.pages.value.isEmpty()) createWelcomePage(graph.repo)
            loaded = true
            graph.sync.start()
        }
        LifecycleResumeEffect(Unit) {
            graph.sync.onResume()
            onPauseOrDispose { }
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (!loaded) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                MainLayout(graph)
            }
        }
    }
}

@Composable
private fun MainLayout(graph: AppGraph) {
    val ui = remember { AppUiState(graph, graph.settings) }
    val pages by graph.repo.pages.collectAsState()
    LaunchedEffect(pages) { ui.ensureValid() }
    PlatformBackHandler(enabled = ui.canGoBack) { ui.back() }

    val modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val mod = if (isApplePlatform) e.isMetaPressed else e.isCtrlPressed
        when {
            mod && (e.key == Key.K || e.key == Key.P) -> { ui.showSearch = true; true }
            mod && e.key == Key.N -> { ui.newPage(null); true }
            mod && e.key == Key.Backslash -> { ui.sidebarVisible = !ui.sidebarVisible; true }
            else -> false
        }
    }

    BoxWithConstraints(modifier) {
        val wide = maxWidth >= 840.dp
        val compact = maxWidth < 600.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                if (ui.sidebarVisible) {
                    Sidebar(graph, ui, showCollapse = true, modifier = Modifier.width(260.dp))
                    VerticalDivider(color = noteColors.divider)
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Content(graph, ui, showMenuButton = !ui.sidebarVisible, onMenu = { ui.sidebarVisible = true }, compact = false)
                }
            }
        } else {
            val drawer = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            LaunchedEffect(ui.drawerCloseSignal) { drawer.close() }
            ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = {
                    ModalDrawerSheet(drawerContainerColor = noteColors.sidebar, modifier = Modifier.width(300.dp)) {
                        Sidebar(graph, ui, showCollapse = false)
                    }
                },
            ) {
                Content(graph, ui, showMenuButton = true, onMenu = { scope.launch { drawer.open() } }, compact = compact)
            }
        }
    }

    if (ui.showSearch) SearchDialog(graph, onOpen = { ui.open(it); ui.showSearch = false }) { ui.showSearch = false }
    if (ui.showSettings) SettingsDialog(graph) { ui.showSettings = false }
    if (ui.showTrash) TrashDialog(graph, ui) { ui.showTrash = false }
    ui.movePageId?.let { MoveDialog(graph, it) { ui.movePageId = null } }
    ui.markdownPageId?.let { MarkdownDialog(graph, it) { ui.markdownPageId = null } }
}

@Composable
private fun Content(graph: AppGraph, ui: AppUiState, showMenuButton: Boolean, onMenu: () -> Unit, compact: Boolean) {
    val id = ui.currentPageId
    if (id == null || graph.repo[id] == null) {
        EmptyState(ui, showMenuButton, onMenu)
    } else {
        key(id) { PageScreen(graph, ui, id, showMenuButton, onMenu, compact) }
    }
}

private suspend fun createWelcomePage(repo: PageRepository) {
    val page = repo.create(null, "S Notes 시작하기")
    repo.setIcon(page.id, "👋")
    fun p(text: String, type: BlockType = BlockType.Paragraph, indent: Int = 0, checked: Boolean = false) =
        Block(type = type, text = text, indent = indent, checked = checked)
    repo.updateBlocks(
        page.id,
        listOf(
            p("S Notes 에 오신 것을 환영합니다! 모든 페이지는 Markdown 으로 저장되고, Google Drive 로 동기화됩니다."),
            p("기본 사용법", BlockType.Heading2),
            p("빈 줄에서 / 를 입력하면 블록 메뉴가 열립니다", BlockType.Todo),
            p("**굵게**, *기울임*, ~~취소선~~, `코드`, [링크](https://www.notion.so) 처럼 Markdown 으로 꾸밀 수 있어요", BlockType.Todo),
            p("Tab 으로 들여쓰기, 왼쪽 손잡이를 끌어서 블록 이동", BlockType.Todo),
            p("사이드바에서 + 를 눌러 하위 페이지 만들기", BlockType.Todo),
            p("Markdown 단축 입력", BlockType.Toggle),
            p("# 제목 1 · ## 제목 2 · ### 제목 3", BlockType.Bulleted, indent = 1),
            p("- 글머리 목록 · 1. 번호 목록 · [] 할 일", BlockType.Bulleted, indent = 1),
            p("> 토글 · \" 인용 · ``` 코드 · --- 구분선", BlockType.Bulleted, indent = 1),
            p("설정에서 Google 계정으로 로그인하면 Drive 의 \"S Notes\" 폴더에 페이지가 .md 파일로 저장됩니다.", BlockType.Callout),
            Block(type = BlockType.Code, text = "---\ntitle: \"S Notes 시작하기\"\n---\n# 이렇게 저장됩니다", language = "markdown"),
            Block(),
        ),
    )
}
