package com.siprikorea.snotes

import com.siprikorea.snotes.markdown.MarkdownCodec
import com.siprikorea.snotes.model.Block
import com.siprikorea.snotes.model.BlockType
import com.siprikorea.snotes.model.Page
import com.siprikorea.snotes.model.PageMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class MarkdownCodecTest {

    private val t = Instant.parse("2026-10-14T12:00:00Z")

    private fun roundTrip(blocks: List<Block>, meta: PageMeta = PageMeta(id = "p1", created = t, updated = t)) {
        val page = Page(meta, blocks)
        val md = MarkdownCodec.serialize(page)
        val back = MarkdownCodec.parse(md)
        assertEquals(meta, back.meta, md)
        assertEquals(blocks.size, back.blocks.size, md)
        blocks.zip(back.blocks).forEach { (a, b) -> assertTrue(a.sameContent(b), "expected $a\nactual   $b\n---\n$md") }
        // 다시 직렬화해도 같은 텍스트 (해시 기반 동기화가 흔들리지 않도록)
        assertEquals(md, MarkdownCodec.serialize(back))
    }

    @Test
    fun allBlockTypesRoundTrip() = roundTrip(
        listOf(
            Block(type = BlockType.Heading1, text = "Big C#"),
            Block(type = BlockType.Heading2, text = "Medium"),
            Block(type = BlockType.Heading3, text = ""),
            Block(text = "Hello **bold** and *italic*\nsecond line\n\nafter blank"),
            Block(text = ""),
            Block(type = BlockType.Bulleted, text = "item"),
            Block(type = BlockType.Bulleted, text = "child\nwrapped", indent = 1),
            Block(type = BlockType.Numbered, text = "one"),
            Block(type = BlockType.Numbered, text = "two"),
            Block(type = BlockType.Numbered, text = "nested", indent = 1),
            Block(type = BlockType.Numbered, text = "three"),
            Block(type = BlockType.Todo, text = "done", checked = true),
            Block(type = BlockType.Todo, text = ""),
            Block(type = BlockType.Quote, text = "quote\n\nmore"),
            Block(type = BlockType.Callout, text = "note"),
            Block(type = BlockType.Code, text = "fun x() {\n\n  ```nested```\n}", language = "kotlin"),
            Block(type = BlockType.Code, text = ""),
            Block(type = BlockType.Divider),
            Block(type = BlockType.Image, text = "cat", url = "https://example.com/c.png"),
            Block(type = BlockType.PageLink, text = "Other", url = "abc-123"),
        ),
    )

    @Test
    fun togglesNestAndClose() = roundTrip(
        listOf(
            Block(type = BlockType.Toggle, text = "a <b> & c"),
            Block(text = "inside", indent = 1),
            Block(type = BlockType.Toggle, text = "inner", indent = 1),
            Block(type = BlockType.Code, text = "x", indent = 2),
            Block(text = "outside"),
            Block(type = BlockType.Toggle, text = "last"),
        ),
    )

    @Test
    fun textThatLooksLikeMarkdownIsEscaped() = roundTrip(
        listOf(
            Block(text = "# not a heading"),
            Block(text = "- not a list\n1. nor this\n> nor quote"),
            Block(text = "\\ backslash"),
            Block(text = "---"),
            Block(text = "&nbsp;"),
            Block(text = "<br>"),
            Block(type = BlockType.Bulleted, text = "[ ] not a todo"),
            Block(type = BlockType.Todo, text = "[x] still text"),
            Block(type = BlockType.Heading1, text = "#hash"),
        ),
    )

    @Test
    fun metaRoundTrip() = roundTrip(
        listOf(Block(text = "x")),
        PageMeta(
            id = "p2", title = "Title: \"quoted\"\twith tab", icon = "📝", parentId = "p1", order = 2.5,
            favorite = true, trashed = true, created = t, updated = Instant.parse("2026-10-15T01:02:03Z"),
            extra = mapOf("tags" to "[a, b]"),
        ),
    )

    @Test
    fun parsesHandWrittenMarkdown() {
        val md = """
            # Shopping
            - milk
            - eggs
              - large
            * [x] bought
            1) first

            plain line
            continues here

            <details><summary>Toggle</summary>
            hidden
            </details>
            > [!TIP] be careful
        """.trimIndent()
        val page = MarkdownCodec.parse(md)
        assertEquals(
            listOf(
                BlockType.Heading1, BlockType.Bulleted, BlockType.Bulleted, BlockType.Bulleted, BlockType.Todo,
                BlockType.Numbered, BlockType.Paragraph, BlockType.Toggle, BlockType.Paragraph, BlockType.Callout,
            ),
            page.blocks.map { it.type },
        )
        assertEquals(1, page.blocks[3].indent)
        assertEquals("plain line\ncontinues here", page.blocks[6].text)
        assertEquals(1, page.blocks[8].indent, "<details> 안의 블록은 토글의 자식")
        assertEquals("be careful", page.blocks[9].text)
        assertTrue(page.meta.id.isNotBlank())
    }

    @Test
    fun numberLabelsRestartAfterOtherBlocks() {
        val blocks = listOf(
            Block(type = BlockType.Numbered), Block(type = BlockType.Numbered),
            Block(type = BlockType.Numbered, indent = 1), Block(type = BlockType.Numbered),
            Block(), Block(type = BlockType.Numbered),
        )
        assertEquals(listOf(1, 2, 1, 3, 0, 1), MarkdownCodec.numberLabels(blocks).toList())
    }
}
