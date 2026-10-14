package com.siprikorea.snotes.ui.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.siprikorea.snotes.model.Block
import com.siprikorea.snotes.model.BlockType
import com.siprikorea.snotes.model.newId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Stable
class EditorBlock(
    val id: String,
    type: BlockType,
    value: TextFieldValue,
    indent: Int,
    checked: Boolean,
    language: String,
    url: String,
) {
    var type by mutableStateOf(type)
    var value by mutableStateOf(value)
    var indent by mutableStateOf(indent)
    var checked by mutableStateOf(checked)
    var language by mutableStateOf(language)
    var url by mutableStateOf(url)

    val text: String get() = value.text

    fun toBlock() = Block(id, type, value.text, indent, checked, language, url)

    companion object {
        fun from(b: Block) = EditorBlock(b.id, b.type, TextFieldValue(b.text), b.indent, b.checked, b.language, b.url)
        fun new(type: BlockType = BlockType.Paragraph, text: String = "", indent: Int = 0) =
            EditorBlock(newId(), type, TextFieldValue(text, TextRange(0)), indent, false, "", "")
    }
}

/** 포커스를 옮겨 달라는 요청. [seq] 가 바뀌어야 같은 블록에도 다시 적용된다. */
data class FocusRequest(val blockId: String, val cursor: Int, val seq: Int)

/** 슬래시(/) 명령 메뉴 상태. */
data class SlashState(val blockId: String, val query: String, val selected: Int = 0)

/**
 * 한 페이지의 블록 편집 상태. 편집은 즉시 화면에 반영되고, [onSave] 는 잠시 쉬었을 때(디바운스) 호출된다.
 */
@Stable
class EditorState(
    val pageId: String,
    initial: List<Block>,
    private val scope: CoroutineScope,
    private val onSave: suspend (List<Block>) -> Unit,
) {
    val blocks = mutableStateListOf<EditorBlock>().apply { addAll(initial.map(EditorBlock::from)) }

    var focusedId by mutableStateOf<String?>(null)
    var focusRequest by mutableStateOf<FocusRequest?>(null)
        private set
    var slash by mutableStateOf<SlashState?>(null)
        private set

    /** 펼쳐진 토글 id. Notion 처럼 기본은 접힘. */
    val expanded = mutableStateListOf<String>()

    /** 이미지 주소/페이지 링크 대상 입력을 기다리는 블록. */
    var pendingImageFor by mutableStateOf<String?>(null)
    var pendingLinkFor by mutableStateOf<String?>(null)

    private var saveJob: Job? = null
    private var focusSeq = 0
    private var lastSaved: List<Block> = initial

    val focused: EditorBlock? get() = focusedId?.let { id -> blocks.firstOrNull { it.id == id } }

    fun indexOf(id: String) = blocks.indexOfFirst { it.id == id }

    // region 화면에 보일 블록 (접힌 토글의 자식 숨김)

    fun visibleBlocks(): List<EditorBlock> {
        val out = ArrayList<EditorBlock>(blocks.size)
        var hideDeeperThan = Int.MAX_VALUE
        for (b in blocks) {
            if (b.indent > hideDeeperThan) continue
            hideDeeperThan = Int.MAX_VALUE
            out += b
            if (b.type == BlockType.Toggle && b.id !in expanded) hideDeeperThan = b.indent
        }
        return out
    }

    fun toggleExpanded(b: EditorBlock) {
        if (b.id in expanded) expanded.remove(b.id) else expanded.add(b.id)
    }

    // endregion

    // region 텍스트 입력

    fun onValueChange(b: EditorBlock, v: TextFieldValue) {
        val old = b.value
        // Enter: 커서 바로 앞에 줄바꿈 하나가 새로 들어온 경우 블록을 나눈다(코드 블록 제외).
        val insertedNewline = b.type != BlockType.Code &&
            v.text.length == old.text.length + 1 && v.selection.collapsed &&
            v.selection.start > 0 && v.text[v.selection.start - 1] == '\n' &&
            v.text.removeRange(v.selection.start - 1, v.selection.start) == old.text
        if (insertedNewline) {
            val at = v.selection.start - 1
            splitAt(b, at)
            return
        }

        if (b.type == BlockType.Paragraph && applyShortcut(b, v)) return

        b.value = v
        updateSlash(b)
        scheduleSave()
    }

    private fun updateSlash(b: EditorBlock) {
        val t = b.text
        slash = if (t.startsWith("/") && '\n' !in t && t.length <= 30 && b.type.hasText) {
            val q = t.substring(1)
            val prev = slash
            SlashState(b.id, q, if (prev?.blockId == b.id && prev.query == q) prev.selected else 0)
        } else {
            null
        }
    }

    /** Notion 식 Markdown 단축 입력. */
    private fun applyShortcut(b: EditorBlock, v: TextFieldValue): Boolean {
        val t = v.text
        val cursor = v.selection.start
        val (type, len) = SHORTCUTS.firstOrNull { (prefix, _) -> t.startsWith(prefix) && cursor == prefix.length }
            ?.let { it.second to it.first.length } ?: return false
        if (type == BlockType.Divider) {
            b.type = BlockType.Divider
            b.value = TextFieldValue("")
            val next = EditorBlock.new(indent = b.indent)
            blocks.add(indexOf(b.id) + 1, next)
            requestFocus(next.id, 0)
        } else {
            b.type = type
            b.value = TextFieldValue(t.substring(len), TextRange(0))
            if (type == BlockType.Todo) b.checked = false
            if (type == BlockType.Toggle) expanded.add(b.id)
        }
        scheduleSave()
        return true
    }

    private fun splitAt(b: EditorBlock, at: Int) {
        val before = b.text.substring(0, at)
        val after = b.text.substring(at)
        val i = indexOf(b.id)

        // 빈 목록 항목에서 Enter: 목록에서 빠져나온다(들여쓰기가 있으면 내어쓰기).
        if (b.type.continuesOnEnter && b.text.isEmpty()) {
            if (b.indent > 0) b.indent-- else b.type = BlockType.Paragraph
            scheduleSave()
            return
        }

        val nextType = when {
            b.type.continuesOnEnter -> b.type
            else -> BlockType.Paragraph
        }
        // 펼친 토글 끝에서 Enter 면 첫 자식으로 만든다.
        val asChild = b.type == BlockType.Toggle && b.id in expanded && after.isEmpty()
        b.value = TextFieldValue(before, TextRange(before.length))
        val next = EditorBlock.new(if (asChild) BlockType.Paragraph else nextType, after, if (asChild) b.indent + 1 else b.indent)
        blocks.add(i + 1, next)
        requestFocus(next.id, 0)
        slash = null
        scheduleSave()
    }

    /** 줄 안에서 줄바꿈(Shift+Enter). 코드 블록에서는 블록을 빠져나와 새 문단을 만든다. */
    fun softBreak(b: EditorBlock) {
        if (b.type == BlockType.Code) {
            insertAfter(b, BlockType.Paragraph)
            return
        }
        val v = b.value
        val t = v.text.substring(0, v.selection.min) + "\n" + v.text.substring(v.selection.max)
        b.value = TextFieldValue(t, TextRange(v.selection.min + 1))
        scheduleSave()
    }

    /** 커서가 맨 앞일 때 Backspace. 처리했으면 true. */
    fun backspaceAtStart(b: EditorBlock): Boolean {
        if (!b.value.selection.collapsed || b.value.selection.start != 0) return false
        val i = indexOf(b.id)
        when {
            b.type != BlockType.Paragraph && b.type.hasText -> b.type = BlockType.Paragraph
            b.indent > 0 -> b.indent--
            i > 0 -> {
                val prev = blocks[i - 1]
                if (!prev.type.hasText) {
                    blocks.removeAt(i - 1)
                } else {
                    val joinAt = prev.text.length
                    prev.value = TextFieldValue(prev.text + b.text, TextRange(joinAt))
                    blocks.removeAt(i)
                    requestFocus(prev.id, joinAt)
                }
            }
            else -> return false
        }
        scheduleSave()
        return true
    }

    /** 커서가 맨 끝일 때 Delete: 다음 블록을 이어 붙인다. */
    fun deleteAtEnd(b: EditorBlock): Boolean {
        if (!b.value.selection.collapsed || b.value.selection.start != b.text.length) return false
        val i = indexOf(b.id)
        val next = blocks.getOrNull(i + 1) ?: return false
        if (next.type.hasText) b.value = TextFieldValue(b.text + next.text, TextRange(b.text.length))
        blocks.removeAt(i + 1)
        scheduleSave()
        return true
    }

    // endregion

    // region 포커스 이동

    fun requestFocus(id: String, cursor: Int) {
        focusRequest = FocusRequest(id, cursor, ++focusSeq)
    }

    fun focusPrevious(b: EditorBlock): Boolean {
        val visible = visibleBlocks()
        val i = visible.indexOfFirst { it.id == b.id }
        val prev = visible.subList(0, i.coerceAtLeast(0)).lastOrNull { it.type.hasText } ?: return false
        requestFocus(prev.id, prev.text.length)
        return true
    }

    fun focusNext(b: EditorBlock): Boolean {
        val visible = visibleBlocks()
        val i = visible.indexOfFirst { it.id == b.id }
        val next = visible.drop(i + 1).firstOrNull { it.type.hasText } ?: return false
        requestFocus(next.id, 0)
        return true
    }

    /** 페이지 제목에서 Enter/↓: 첫 블록으로. 없으면 만든다. */
    fun focusFirst() {
        val first = blocks.firstOrNull { it.type.hasText } ?: EditorBlock.new().also { blocks.add(0, it); scheduleSave() }
        requestFocus(first.id, 0)
    }

    /** 빈 공간 클릭: 마지막 블록이 빈 문단이면 거기로, 아니면 새 문단. */
    fun focusEnd() {
        val last = blocks.lastOrNull()
        if (last != null && last.type == BlockType.Paragraph && last.text.isEmpty() && last.indent == 0) {
            requestFocus(last.id, 0)
        } else {
            val b = EditorBlock.new()
            blocks.add(b)
            requestFocus(b.id, 0)
            scheduleSave()
        }
    }

    // endregion

    // region 블록 조작

    fun insertAfter(b: EditorBlock, type: BlockType = BlockType.Paragraph): EditorBlock {
        val next = EditorBlock.new(type, indent = b.indent)
        blocks.add(indexOf(b.id) + 1, next)
        if (type.hasText) requestFocus(next.id, 0)
        scheduleSave()
        return next
    }

    fun turnInto(b: EditorBlock, type: BlockType) {
        b.type = type
        if (type == BlockType.Toggle) expanded.add(b.id)
        if (!type.hasText) b.value = TextFieldValue("")
        when (type) {
            BlockType.Divider -> if (indexOf(b.id) == blocks.lastIndex) insertAfter(b)
            BlockType.Image -> pendingImageFor = b.id
            BlockType.PageLink -> pendingLinkFor = b.id
            else -> requestFocus(b.id, b.text.length)
        }
        scheduleSave()
    }

    fun setChecked(b: EditorBlock, checked: Boolean) {
        b.checked = checked
        scheduleSave()
    }

    fun setLanguage(b: EditorBlock, language: String) {
        b.language = language
        scheduleSave()
    }

    fun setUrl(b: EditorBlock, url: String, text: String? = null) {
        b.url = url
        if (text != null) b.value = TextFieldValue(text)
        scheduleSave()
    }

    /** 들여쓰기. 바로 위 블록보다 한 단계까지만 깊어질 수 있다. 자식 블록도 함께 움직인다. */
    fun indent(b: EditorBlock, delta: Int) {
        val i = indexOf(b.id)
        val maxIndent = if (i == 0) 0 else blocks[i - 1].indent + 1
        val target = (b.indent + delta).coerceIn(0, maxIndent)
        val diff = target - b.indent
        if (diff == 0) return
        val range = subtreeRange(i)
        for (k in range) blocks[k].indent = (blocks[k].indent + diff).coerceAtLeast(0)
        scheduleSave()
    }

    fun delete(b: EditorBlock) {
        val i = indexOf(b.id)
        if (i < 0) return
        blocks.removeAt(i)
        if (blocks.isEmpty()) blocks.add(EditorBlock.new())
        blocks.getOrNull((i - 1).coerceAtLeast(0))?.let { if (it.type.hasText) requestFocus(it.id, it.text.length) }
        scheduleSave()
    }

    fun duplicate(b: EditorBlock) {
        val c = EditorBlock.new(b.type, b.text, b.indent).also {
            it.checked = b.checked; it.language = b.language; it.url = b.url
        }
        blocks.add(indexOf(b.id) + 1, c)
        scheduleSave()
    }

    /** 블록(과 그 자식들)을 바로 위/아래 형제와 자리를 바꾼다. */
    fun moveBy(b: EditorBlock, delta: Int) {
        val i = indexOf(b.id)
        if (delta > 0) {
            val next = blocks.getOrNull(subtreeRange(i).last + 1) ?: return
            if (next.indent != b.indent) return
            moveTo(b, next, after = true)
        } else {
            var k = i - 1
            while (k >= 0 && blocks[k].indent > b.indent) k--
            val prev = blocks.getOrNull(k) ?: return
            if (prev.indent != b.indent) return
            moveTo(b, prev, after = false)
        }
    }

    /** 드래그 앤 드롭: [b] 와 그 자식들을 [target] 앞(또는 뒤)으로 옮긴다. */
    fun moveTo(b: EditorBlock, target: EditorBlock, after: Boolean) {
        val from = indexOf(b.id)
        val range = subtreeRange(from)
        if (indexOf(target.id) in range) return
        val moving = range.map { blocks[it] }
        repeat(moving.size) { blocks.removeAt(from) }
        val ti = indexOf(target.id)
        val insertAt = if (after) subtreeRange(ti).last + 1 else ti
        val diff = target.indent - b.indent
        moving.forEach { it.indent = (it.indent + diff).coerceAtLeast(0) }
        blocks.addAll(insertAt, moving)
        requestFocus(b.id, b.value.selection.start)
        scheduleSave()
    }

    /** 블록 i 와 그 자식들(뒤따르는 더 깊은 블록)의 인덱스 범위. */
    private fun subtreeRange(i: Int): IntRange {
        var end = i
        while (end + 1 < blocks.size && blocks[end + 1].indent > blocks[i].indent) end++
        return i..end
    }

    // endregion

    // region 슬래시 메뉴

    fun slashItems(): List<BlockType> {
        val q = slash?.query?.trim()?.lowercase() ?: return emptyList()
        return BlockType.entries.filter { q.isEmpty() || it.label.lowercase().contains(q) || it.keywords.contains(q) }
    }

    fun moveSlashSelection(delta: Int) {
        val s = slash ?: return
        val n = slashItems().size
        if (n == 0) return
        slash = s.copy(selected = (s.selected + delta).mod(n))
    }

    fun applySlash(type: BlockType? = null) {
        val s = slash ?: return
        val b = blocks.firstOrNull { it.id == s.blockId } ?: return
        val chosen = type ?: slashItems().getOrNull(s.selected) ?: return
        slash = null
        b.value = TextFieldValue("")
        turnInto(b, chosen)
    }

    fun closeSlash() {
        slash = null
    }

    /** '/' 만 입력하는 것과 같다(툴바의 + 버튼). */
    fun openSlash(b: EditorBlock) {
        if (b.text.isNotEmpty()) {
            val n = insertAfter(b)
            n.value = TextFieldValue("/", TextRange(1))
            updateSlash(n)
        } else {
            b.value = TextFieldValue("/", TextRange(1))
            updateSlash(b)
            requestFocus(b.id, 1)
        }
    }

    // endregion

    // region 인라인 서식

    fun format(b: EditorBlock, f: InlineFormat) {
        b.value = b.value.toggleFormat(f)
        scheduleSave()
    }

    fun link(b: EditorBlock, url: String) {
        b.value = b.value.insertLink(url)
        scheduleSave()
    }

    // endregion

    // region 저장

    fun snapshot(): List<Block> = blocks.map { it.toBlock() }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            flushNow()
        }
    }

    suspend fun flushNow() {
        val s = snapshot()
        lastSaved = s
        onSave(s)
    }

    val hasPendingSave: Boolean get() = saveJob?.isActive == true

    /** 다른 기기에서 동기화된 내용으로 교체한다(편집 중이 아닐 때만). */
    fun replaceFromRepository(newBlocks: List<Block>) {
        if (hasPendingSave) return
        val same = newBlocks.size == lastSaved.size && newBlocks.zip(lastSaved).all { (a, b) -> a.sameContent(b) }
        if (same) return
        lastSaved = newBlocks
        blocks.clear()
        blocks.addAll(newBlocks.map(EditorBlock::from))
        slash = null
    }

    // endregion

    companion object {
        private val SHORTCUTS: List<Pair<String, BlockType>> = listOf(
            "### " to BlockType.Heading3,
            "## " to BlockType.Heading2,
            "# " to BlockType.Heading1,
            "[] " to BlockType.Todo,
            "[ ] " to BlockType.Todo,
            "- " to BlockType.Bulleted,
            "* " to BlockType.Bulleted,
            "+ " to BlockType.Bulleted,
            "1. " to BlockType.Numbered,
            "> " to BlockType.Toggle,
            "\" " to BlockType.Quote,
            "```" to BlockType.Code,
            "---" to BlockType.Divider,
        )
    }
}
