package com.siprikorea.snotes

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.siprikorea.snotes.model.Block
import com.siprikorea.snotes.model.BlockType
import com.siprikorea.snotes.ui.editor.EditorBlock
import com.siprikorea.snotes.ui.editor.EditorState
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditorStateTest {

    private fun TestScope.editor(vararg blocks: Block, saved: MutableList<List<Block>> = mutableListOf()) =
        EditorState("p", blocks.toList(), backgroundScope) { saved += it }

    /** 사용자가 커서 위치에 [s] 를 입력한 것처럼. */
    private fun EditorState.type(b: EditorBlock, s: String) {
        val v = b.value
        val at = v.selection.start
        onValueChange(b, TextFieldValue(v.text.substring(0, at) + s + v.text.substring(at), TextRange(at + s.length)))
    }

    private fun EditorState.cursor(b: EditorBlock, at: Int) { b.value = b.value.copy(selection = TextRange(at)) }

    @Test
    fun enterSplitsBlockAndContinuesLists() = runTest {
        val e = editor(Block(type = BlockType.Bulleted, text = "helloworld"))
        val b = e.blocks[0]
        e.cursor(b, 5)
        e.type(b, "\n")
        assertEquals(listOf("hello", "world"), e.blocks.map { it.text })
        assertEquals(BlockType.Bulleted, e.blocks[1].type)
        assertEquals(e.blocks[1].id, e.focusRequest?.blockId)

        // 빈 목록 항목에서 Enter → 일반 문단으로
        val c = e.blocks[1]
        c.value = TextFieldValue("")
        e.type(c, "\n")
        assertEquals(BlockType.Paragraph, c.type)
        assertEquals(2, e.blocks.size)
    }

    @Test
    fun markdownShortcutsConvertParagraph() = runTest {
        val e = editor(Block())
        val b = e.blocks[0]
        e.type(b, "#"); e.type(b, " ")
        assertEquals(BlockType.Heading1, b.type)
        assertEquals("", b.text)

        val t = e.insertAfter(b)
        "[] ".forEach { e.type(t, it.toString()) }
        assertEquals(BlockType.Todo, t.type)

        val d = e.insertAfter(t)
        "---".forEach { e.type(d, it.toString()) }
        assertEquals(BlockType.Divider, d.type)
        assertEquals(BlockType.Paragraph, e.blocks.last().type, "구분선 뒤에 새 문단")
    }

    @Test
    fun backspaceAtStartResetsTypeThenMerges() = runTest {
        val e = editor(Block(text = "abc"), Block(type = BlockType.Quote, text = "def"))
        val q = e.blocks[1]
        e.cursor(q, 0)
        e.backspaceAtStart(q)
        assertEquals(BlockType.Paragraph, q.type)
        e.backspaceAtStart(q)
        assertEquals(listOf("abcdef"), e.blocks.map { it.text })
        assertEquals(3, e.focusRequest?.cursor)
    }

    @Test
    fun indentIsLimitedAndCarriesChildren() = runTest {
        val e = editor(
            Block(type = BlockType.Bulleted, text = "a"),
            Block(type = BlockType.Bulleted, text = "b"),
            Block(type = BlockType.Bulleted, text = "c", indent = 1),
        )
        val (_, b, c) = e.blocks
        e.indent(b, 1)
        assertEquals(listOf(0, 1, 2), e.blocks.map { it.indent }, "자식도 함께 들여쓰기")
        e.indent(b, 1)
        assertEquals(1, b.indent, "위 블록보다 두 단계 깊어질 수 없다")
        e.indent(e.blocks[0], 1)
        assertEquals(0, e.blocks[0].indent)
        e.indent(c, -1)
        assertEquals(1, c.indent)
    }

    @Test
    fun slashMenuFiltersAndApplies() = runTest {
        val e = editor(Block())
        val b = e.blocks[0]
        "/code".forEach { e.type(b, it.toString()) }
        assertEquals(listOf(BlockType.Code), e.slashItems())
        e.applySlash()
        assertEquals(BlockType.Code, b.type)
        assertEquals("", b.text)
        assertNull(e.slash)

        val p = e.insertAfter(b)
        "/할".forEach { e.type(p, it.toString()) }
        assertEquals(listOf(BlockType.Todo), e.slashItems())
    }

    @Test
    fun moveCarriesChildrenAndTogglesHideChildren() = runTest {
        val e = editor(
            Block(type = BlockType.Toggle, text = "t"),
            Block(text = "child", indent = 1),
            Block(text = "x"),
        )
        assertEquals(listOf("t", "x"), e.visibleBlocks().map { it.text }, "토글은 기본으로 접힘")
        e.toggleExpanded(e.blocks[0])
        assertEquals(3, e.visibleBlocks().size)

        e.moveBy(e.blocks[0], 1)
        assertEquals(listOf("x", "t", "child"), e.blocks.map { it.text })
        e.moveBy(e.blocks[1], -1)
        assertEquals(listOf("t", "child", "x"), e.blocks.map { it.text })
        e.moveBy(e.blocks[1], -1)
        assertEquals(listOf("t", "child", "x"), e.blocks.map { it.text }, "자식은 부모 밖으로 나가지 않는다")
    }

    @Test
    fun editsAreSavedAfterDebounce() = runTest {
        val saved = mutableListOf<List<Block>>()
        val e = editor(Block(), saved = saved)
        "hi".forEach { e.type(e.blocks[0], it.toString()) }
        advanceTimeBy(100)
        assertEquals(0, saved.size)
        advanceTimeBy(500)
        assertEquals(1, saved.size, "디바운스로 한 번만 저장")
        assertEquals("hi", saved.last().single().text)
    }
}
