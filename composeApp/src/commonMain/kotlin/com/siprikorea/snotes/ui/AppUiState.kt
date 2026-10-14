package com.siprikorea.snotes.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings
import com.siprikorea.snotes.AppGraph
import com.siprikorea.snotes.model.Block
import com.siprikorea.snotes.model.BlockType
import kotlinx.coroutines.launch

/** 화면 이동과 대화상자 상태. */
@Stable
class AppUiState(private val graph: AppGraph, private val settings: Settings) {
    var currentPageId by mutableStateOf(settings.getStringOrNull(LAST_PAGE))
        private set
    private val backStack = mutableStateListOf<String>()

    /** 사이드바 트리에서 펼친 페이지. */
    val expanded = mutableStateListOf<String>()

    var sidebarVisible by mutableStateOf(true)
    var showSearch by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var showTrash by mutableStateOf(false)
    var movePageId by mutableStateOf<String?>(null)
    var markdownPageId by mutableStateOf<String?>(null)

    /** 좁은 화면에서 서랍을 닫아 달라는 신호. */
    var drawerCloseSignal by mutableStateOf(0)

    val canGoBack: Boolean get() = backStack.isNotEmpty()

    fun open(id: String) {
        if (id == currentPageId) return
        currentPageId?.let { backStack.add(it) }
        setCurrent(id)
        graph.repo.ancestors(id).forEach { if (it.id !in expanded) expanded.add(it.id) }
        drawerCloseSignal++
    }

    fun back(): Boolean {
        while (backStack.isNotEmpty()) {
            val id = backStack.removeAt(backStack.lastIndex)
            if (graph.repo[id] != null) {
                setCurrent(id)
                return true
            }
        }
        return false
    }

    /** 현재 페이지가 사라졌을 때(영구 삭제 등) 다른 페이지로. */
    fun ensureValid() {
        val cur = currentPageId
        if (cur != null && graph.repo[cur] != null) return
        val next = graph.repo.children(null).firstOrNull()?.id
        setCurrent(next)
    }

    private fun setCurrent(id: String?) {
        currentPageId = id
        if (id == null) settings.remove(LAST_PAGE) else settings.putString(LAST_PAGE, id)
    }

    fun toggleExpanded(id: String) {
        if (id in expanded) expanded.remove(id) else expanded.add(id)
    }

    /** 새 페이지. 부모가 있으면 부모 본문 끝에 페이지 링크 블록도 붙인다(Notion 의 하위 페이지 블록처럼). */
    fun newPage(parentId: String? = null) {
        graph.scope.launch {
            val page = graph.repo.create(parentId)
            if (parentId != null) {
                graph.repo[parentId]?.let { parent ->
                    val blocks = parent.blocks.dropLastWhile { it.type == BlockType.Paragraph && it.text.isEmpty() && parent.blocks.size > 1 }
                    graph.repo.updateBlocks(parentId, blocks + Block(type = BlockType.PageLink, url = page.id))
                }
                if (parentId !in expanded) expanded.add(parentId)
            }
            open(page.id)
        }
    }

    private companion object {
        const val LAST_PAGE = "last_page"
    }
}
