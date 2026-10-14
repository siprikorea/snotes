package com.siprikorea.snotes.markdown

import com.siprikorea.snotes.model.Block
import com.siprikorea.snotes.model.BlockType
import com.siprikorea.snotes.model.Page
import com.siprikorea.snotes.model.PageMeta
import com.siprikorea.snotes.model.newId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/**
 * 페이지 <-> Markdown 변환.
 *
 * 형식:
 *  - YAML frontmatter(id, title, icon, parent, order, favorite, trashed, created, updated + 알 수 없는 키 보존)
 *  - 블록마다 빈 줄로 구분. 들여쓰기 1단계 = 공백 2칸.
 *  - GFM 으로 표현할 수 있는 블록은 GFM 그대로, 토글은 `<details>`, 콜아웃은 `> [!NOTE]`,
 *    빈 문단은 `&nbsp;`, 페이지 링크는 `[📄 제목](snotes://page/<id>)`.
 *  - 블록 문법으로 오해될 수 있는 텍스트 줄은 앞에 `\` 를 붙여 이스케이프한다.
 *
 * 직접 편집한 Markdown 파일(Drive 에서 수정 등)도 최대한 관대하게 읽는다.
 */
object MarkdownCodec {

    const val PAGE_LINK_SCHEME = "snotes://page/"

    // region serialize

    fun serialize(page: Page): String = buildString {
        writeFrontmatter(page.meta)
        append('\n')
        append(serializeBlocks(page.blocks))
    }

    private fun StringBuilder.writeFrontmatter(m: PageMeta) {
        append("---\n")
        append("id: ").append(m.id).append('\n')
        append("title: ").append(quote(m.title)).append('\n')
        if (m.icon.isNotEmpty()) append("icon: ").append(quote(m.icon)).append('\n')
        append("parent: ").append(m.parentId ?: "").append('\n')
        append("order: ").append(formatOrder(m.order)).append('\n')
        append("favorite: ").append(m.favorite).append('\n')
        if (m.trashed) append("trashed: true\n")
        append("created: ").append(m.created.toString()).append('\n')
        append("updated: ").append(m.updated.toString()).append('\n')
        for ((k, v) in m.extra) append(k).append(": ").append(v).append('\n')
        append("---\n")
    }

    fun serializeBlocks(blocks: List<Block>): String = buildString {
        val numbers = numberLabels(blocks)
        val openToggles = ArrayDeque<Int>()
        fun closeToggle() {
            append(prefix(openToggles.removeLast())).append("</details>\n\n")
        }
        blocks.forEachIndexed { i, b ->
            while (openToggles.isNotEmpty() && b.indent <= openToggles.last()) closeToggle()
            writeBlock(b, numbers[i])
            append('\n')
            if (b.type == BlockType.Toggle) openToggles.addLast(b.indent)
        }
        while (openToggles.isNotEmpty()) closeToggle()
    }.trimEnd('\n').let { if (it.isEmpty()) it else it + "\n" }

    private fun StringBuilder.writeBlock(b: Block, number: Int) {
        val p = prefix(b.indent)
        val lines = b.text.split('\n')
        fun listItem(marker: String) {
            append(p).append(marker).append(escapeLine(lines.first())).append('\n')
            val cont = p + " ".repeat(marker.length.coerceAtMost(4))
            lines.drop(1).forEach { append(cont).append(escapeContinuation(it)).append('\n') }
        }
        when (b.type) {
            BlockType.Paragraph ->
                if (b.text.isEmpty()) {
                    append(p).append("&nbsp;\n")
                } else {
                    lines.forEach { append(p).append(escapeContinuation(it)).append('\n') }
                }
            BlockType.Heading1 -> append(p).append("# ").append(oneLine(b.text)).append('\n')
            BlockType.Heading2 -> append(p).append("## ").append(oneLine(b.text)).append('\n')
            BlockType.Heading3 -> append(p).append("### ").append(oneLine(b.text)).append('\n')
            BlockType.Bulleted -> listItem("- ")
            BlockType.Numbered -> listItem("$number. ")
            BlockType.Todo -> listItem(if (b.checked) "- [x] " else "- [ ] ")
            BlockType.Quote -> lines.forEach { append(p).append(if (it.isEmpty()) ">" else "> $it").append('\n') }
            BlockType.Callout -> {
                append(p).append("> [!NOTE]\n")
                lines.forEach { append(p).append(if (it.isEmpty()) ">" else "> $it").append('\n') }
            }
            BlockType.Code -> {
                var fence = "```"
                while (b.text.contains(fence)) fence += "`"
                append(p).append(fence).append(b.language).append('\n')
                if (b.text.isNotEmpty()) lines.forEach { append(p).append(it).append('\n') }
                append(p).append(fence).append('\n')
            }
            BlockType.Divider -> append(p).append("---\n")
            BlockType.Image -> append(p).append("![").append(oneLine(b.text)).append("](").append(b.url).append(")\n")
            BlockType.PageLink ->
                append(p).append("[📄 ").append(oneLine(b.text)).append("](").append(PAGE_LINK_SCHEME).append(b.url).append(")\n")
            BlockType.Toggle -> {
                append(p).append("<details>\n")
                append(p).append("<summary>").append(htmlEscape(oneLine(b.text))).append("</summary>\n")
            }
        }
    }

    /** 번호 목록의 표시 번호. Numbered 가 아닌 블록은 0. UI 와 직렬화가 같은 규칙을 쓴다. */
    fun numberLabels(blocks: List<Block>): IntArray {
        val counters = HashMap<Int, Int>()
        return IntArray(blocks.size) { i ->
            val b = blocks[i]
            counters.keys.filter { it > b.indent }.forEach { counters.remove(it) }
            if (b.type == BlockType.Numbered) {
                val n = (counters[b.indent] ?: 0) + 1
                counters[b.indent] = n
                n
            } else {
                counters.remove(b.indent)
                0
            }
        }
    }

    private fun prefix(indent: Int) = "  ".repeat(indent)
    private fun oneLine(s: String) = s.replace('\n', ' ')
    private fun quote(s: String) = JsonPrimitive(s).toString()
    private fun formatOrder(d: Double) =
        if (d % 1.0 == 0.0 && d > -1e15 && d < 1e15) d.toLong().toString() else d.toString()

    private fun htmlEscape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private fun htmlUnescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    private fun escapeLine(s: String): String =
        if (isBlockStart(s) || s.startsWith("\\") || s == "<br>" || TODO_TEXT.containsMatchIn(s)) "\\" + s else s

    /** 문단/목록의 둘째 줄 이후. 빈 줄은 블록 경계로 읽히므로 `<br>` 로 표시한다. */
    private fun escapeContinuation(s: String): String = if (s.isEmpty()) "<br>" else escapeLine(s)

    // endregion

    // region parse

    fun parse(text: String, fallbackId: () -> String = ::newId): Page {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var start = 0
        val fm = LinkedHashMap<String, String>()
        if (lines.firstOrNull()?.trim() == "---") {
            val end = (1 until lines.size).firstOrNull { lines[it].trim() == "---" }
            if (end != null) {
                for (i in 1 until end) {
                    val line = lines[i]
                    val colon = line.indexOf(':')
                    if (colon <= 0) continue
                    fm[line.substring(0, colon).trim()] = line.substring(colon + 1).trim()
                }
                start = end + 1
            }
        }
        val meta = metaFrom(fm, fallbackId)
        return Page(meta, parseBlocks(lines.subList(start, lines.size)).ifEmpty { listOf(Block()) })
    }

    private val KNOWN_KEYS = setOf("id", "title", "icon", "parent", "order", "favorite", "trashed", "created", "updated")

    private fun metaFrom(fm: Map<String, String>, fallbackId: () -> String): PageMeta {
        fun str(key: String) = fm[key]?.let(::unquote)
        val created = str("created")?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val updated = str("updated")?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val base = PageMeta(id = str("id")?.ifBlank { null } ?: fallbackId())
        return base.copy(
            title = str("title") ?: "",
            icon = str("icon") ?: "",
            parentId = str("parent")?.ifBlank { null },
            order = str("order")?.toDoubleOrNull() ?: 0.0,
            favorite = str("favorite") == "true",
            trashed = str("trashed") == "true",
            created = created ?: updated ?: base.created,
            updated = updated ?: created ?: base.updated,
            extra = fm.filterKeys { it !in KNOWN_KEYS },
        )
    }

    private fun unquote(v: String): String = when {
        v.length >= 2 && v.startsWith('"') && v.endsWith('"') ->
            runCatching { Json.decodeFromString<String>(v) }.getOrElse { v.substring(1, v.length - 1) }
        v.length >= 2 && v.startsWith('\'') && v.endsWith('\'') -> v.substring(1, v.length - 1).replace("''", "'")
        else -> v
    }

    fun parseBlocks(rawLines: List<String>): List<Block> {
        val lines = rawLines.map { it.replace("\t", "  ") }
        val out = mutableListOf<Block>()
        // <details> 안의 블록은 들여쓰기가 없어도 토글의 자식으로 본다.
        val toggleStack = ArrayDeque<Int>()
        var i = 0

        fun leading(line: String) = line.length - line.trimStart().length

        /** 바로 다음 줄들 중 블록 시작이 아닌 줄(같은 블록의 이어지는 줄). */
        fun continuation(): List<String> {
            val acc = mutableListOf<String>()
            while (i < lines.size) {
                val c = lines[i].trim()
                if (c.isEmpty() || isBlockStart(c)) break
                acc += if (c == "<br>") "" else unescape(c)
                i++
            }
            return acc
        }

        while (i < lines.size) {
            val line = lines[i]
            val content = line.trim()
            if (content.isEmpty()) { i++; continue }
            val spaces = leading(line)
            val indent = maxOf(spaces / 2, (toggleStack.lastOrNull() ?: -1) + 1)
            i++

            fun add(b: Block) { out += b.copy(indent = indent) }
            fun withCont(first: String) = (listOf(first) + continuation()).joinToString("\n")

            when {
                content.startsWith("```") || content.startsWith("~~~") -> {
                    val fenceChar = content[0]
                    val fence = content.takeWhile { it == fenceChar }
                    val lang = content.substring(fence.length).trim()
                    val body = mutableListOf<String>()
                    while (i < lines.size && !lines[i].trim().let { it.startsWith(fence) && it.trimEnd(fenceChar).isEmpty() }) {
                        val l = lines[i]
                        body += l.substring(minOf(spaces, leading(l)))
                        i++
                    }
                    i++ // 닫는 펜스
                    add(Block(type = BlockType.Code, text = body.joinToString("\n"), language = lang))
                }
                content.startsWith("<details") -> {
                    var rest = content.substringAfter('>', "").trim()
                    if (rest.isEmpty()) {
                        while (i < lines.size && lines[i].isBlank()) i++
                        if (i < lines.size && lines[i].trim().startsWith("<summary")) rest = lines[i++].trim()
                    }
                    val summary = SUMMARY.find(rest)?.groupValues?.get(1) ?: ""
                    add(Block(type = BlockType.Toggle, text = htmlUnescape(summary.trim())))
                    toggleStack.addLast(indent)
                }
                content.startsWith("</details") -> {
                    toggleStack.removeLastOrNull()
                }
                content == "&nbsp;" -> add(Block())
                DIVIDER.matches(content) -> add(Block(type = BlockType.Divider))
                HEADING.containsMatchIn(content) -> {
                    val level = content.takeWhile { it == '#' }.length
                    val type = when (level) {
                        1 -> BlockType.Heading1
                        2 -> BlockType.Heading2
                        else -> BlockType.Heading3
                    }
                    add(Block(type = type, text = content.substring(level).trim()))
                }
                TODO.containsMatchIn(content) -> {
                    val m = TODO.find(content)!!
                    val checked = m.groupValues[1].equals("x", ignoreCase = true)
                    add(Block(type = BlockType.Todo, checked = checked, text = withCont(unescape(content.substring(m.range.last + 1)))))
                }
                BULLET.containsMatchIn(content) ->
                    add(Block(type = BlockType.Bulleted, text = withCont(unescape(content.substring(2.coerceAtMost(content.length))))))
                NUMBERED.containsMatchIn(content) -> {
                    val m = NUMBERED.find(content)!!
                    add(Block(type = BlockType.Numbered, text = withCont(unescape(content.substring(m.range.last + 1)))))
                }
                content.startsWith(">") -> {
                    val quoted = mutableListOf(stripQuote(content))
                    while (i < lines.size && lines[i].trim().startsWith(">")) quoted += stripQuote(lines[i++].trim())
                    val alert = ALERT.find(quoted.first())
                    if (alert != null) {
                        val first = quoted.first().substring(alert.range.last + 1).trim()
                        val body = (if (first.isEmpty()) emptyList() else listOf(first)) + quoted.drop(1)
                        add(Block(type = BlockType.Callout, text = body.joinToString("\n")))
                    } else {
                        add(Block(type = BlockType.Quote, text = quoted.joinToString("\n")))
                    }
                }
                PAGE_LINK.matches(content) -> {
                    val m = PAGE_LINK.find(content)!!
                    add(Block(type = BlockType.PageLink, text = m.groupValues[1], url = m.groupValues[2]))
                }
                IMAGE.matches(content) -> {
                    val m = IMAGE.find(content)!!
                    add(Block(type = BlockType.Image, text = m.groupValues[1], url = m.groupValues[2]))
                }
                else -> {
                    val first = if (content == "<br>") "" else unescape(content)
                    add(Block(type = BlockType.Paragraph, text = withCont(first)))
                }
            }
        }
        return out
    }

    private fun stripQuote(s: String) = s.removePrefix(">").let { if (it.startsWith(" ")) it.substring(1) else it }

    private fun unescape(s: String) = if (s.startsWith("\\")) s.substring(1) else s

    // endregion

    private val DIVIDER = Regex("""^(-{3,}|\*{3,}|_{3,})$""")
    private val HEADING = Regex("""^#{1,6}(\s|$)""")
    private val TODO = Regex("""^[-*+] \[([ xX])\]( |$)""")
    private val TODO_TEXT = Regex("""^\[[ xX]\]( |$)""")
    private val BULLET = Regex("""^[-*+](\s|$)""")
    private val NUMBERED = Regex("""^\d{1,9}[.)](\s|$)""")
    private val ALERT = Regex("""^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]""", RegexOption.IGNORE_CASE)
    private val IMAGE = Regex("""^!\[(.*)\]\((.*)\)$""")
    private val PAGE_LINK = Regex("""^\[(?:📄 ?)?(.*)\]\(snotes://page/([^)\s]+)\)$""")
    private val SUMMARY = Regex("""<summary>(.*?)</summary>""")
    private val SETEXT = Regex("""^=+$""")

    /** 이 줄이 (문단 텍스트가 아니라) 새 블록의 시작으로 읽히는지. */
    internal fun isBlockStart(c: String): Boolean =
        c.startsWith("```") || c.startsWith("~~~") || c.startsWith("<details") || c.startsWith("</details") ||
            c.startsWith("<summary") || c == "&nbsp;" || c.startsWith(">") ||
            DIVIDER.matches(c) || SETEXT.matches(c) || HEADING.containsMatchIn(c) || BULLET.containsMatchIn(c) ||
            NUMBERED.containsMatchIn(c) || IMAGE.matches(c) || PAGE_LINK.matches(c)
}
