package com.siprikorea.snotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import com.siprikorea.snotes.isApplePlatform
import com.siprikorea.snotes.markdown.MarkdownCodec
import com.siprikorea.snotes.model.BlockType
import com.siprikorea.snotes.ui.theme.NoteColors
import com.siprikorea.snotes.ui.theme.noteColors

/** 드래그 중인 블록과 놓일 위치. */
private data class DropTarget(val blockId: String, val after: Boolean)

/**
 * 페이지 본문 편집기. [header] 는 아이콘/제목 영역으로 목록 맨 위에 들어간다.
 * [pageTitle] 은 PageLink 블록이 가리키는 페이지 제목을 찾는 데 쓴다.
 */
@Composable
fun BlockEditor(
    state: EditorState,
    pageTitle: (String) -> String?,
    onOpenPage: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: androidx.compose.ui.unit.Dp = 24.dp,
    header: LazyListScope.() -> Unit,
) {
    val colors = noteColors
    val visible = state.visibleBlocks()
    val numbers = run {
        val labels = MarkdownCodec.numberLabels(state.blocks.map { it.toBlock() })
        state.blocks.mapIndexed { i, b -> b.id to labels[i] }.toMap()
    }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dropTarget by remember { mutableStateOf<DropTarget?>(null) }

    // 포커스 요청 대상이 화면 밖이면 먼저 스크롤한다.
    LaunchedEffect(state.focusRequest) {
        val req = state.focusRequest ?: return@LaunchedEffect
        val idx = visible.indexOfFirst { it.id == req.blockId }
        if (idx >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.key == req.blockId }) {
            listState.animateScrollToItem(idx + HEADER_ITEMS)
        }
    }

    LazyColumn(state = listState, modifier = modifier) {
        header()
        items(visible, key = { it.id }) { b ->
            val target = dropTarget?.takeIf { it.blockId == b.id && dragging != b.id }
            CenteredColumn(contentPadding) {
                BlockRow(
                    state = state,
                    b = b,
                    number = numbers[b.id] ?: 0,
                    colors = colors,
                    pageTitle = pageTitle,
                    onOpenPage = onOpenPage,
                    dragging = dragging == b.id,
                    onDragStart = { dragging = b.id },
                    onDrag = { pointerY ->
                        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == b.id }
                        val y = (item?.offset ?: 0) + pointerY
                        val over = listState.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size && it.key is String && it.key != HEADER_KEY && it.key != FOOTER_KEY }
                        dropTarget = over?.let { DropTarget(it.key as String, y > it.offset + it.size / 2) }
                    },
                    onDragEnd = {
                        val t = dropTarget
                        val target = t?.let { tt -> state.blocks.firstOrNull { it.id == tt.blockId } }
                        if (t != null && target != null && target.id != b.id) state.moveTo(b, target, t.after)
                        dragging = null
                        dropTarget = null
                    },
                )
                if (target != null) {
                    Box(
                        Modifier.align(if (target.after) Alignment.BottomStart else Alignment.TopStart)
                            .fillMaxWidth().height(3.dp).background(colors.accent.copy(alpha = 0.6f)),
                    )
                }
            }
        }
        item(key = FOOTER_KEY) {
            // 본문 아래 빈 곳을 누르면 새 문단에서 이어 쓴다(Notion 과 같은 동작).
            Box(
                Modifier.fillMaxWidth().height(240.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.focusEnd() },
            )
        }
    }
}

/** 넓은 화면에서 본문 폭을 제한하고 가운데 정렬한다. */
@Composable
fun CenteredColumn(contentPadding: androidx.compose.ui.unit.Dp, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = MAX_WIDTH).fillMaxWidth().padding(horizontal = contentPadding), content = content)
    }
}

const val HEADER_KEY = "__header"
private const val FOOTER_KEY = "__footer"
private const val HEADER_ITEMS = 1
val MAX_WIDTH = 760.dp

@Composable
private fun BlockRow(
    state: EditorState,
    b: EditorBlock,
    number: Int,
    colors: NoteColors,
    pageTitle: (String) -> String?,
    onOpenPage: (String) -> Unit,
    dragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val focused = state.focusedId == b.id
    val topPad = when (b.type) {
        BlockType.Heading1 -> 18.dp
        BlockType.Heading2 -> 14.dp
        BlockType.Heading3 -> 10.dp
        else -> 2.dp
    }

    Row(
        Modifier.fillMaxWidth().hoverable(hover).padding(top = topPad, bottom = 2.dp)
            .background(if (dragging) colors.selected else Color.Transparent, RoundedCornerShape(4.dp)),
        verticalAlignment = Alignment.Top,
    ) {
        // 왼쪽 손잡이: 메뉴 열기 + 끌어서 옮기기
        Box(Modifier.width(24.dp).padding(top = handleTop(b.type))) {
            if (hovered || focused || menuOpen || dragging) {
                var startY by remember { mutableStateOf(0f) }
                var dy by remember { mutableStateOf(0f) }
                Icon(
                    Icons.Outlined.DragIndicator,
                    contentDescription = "블록 메뉴",
                    tint = colors.faint,
                    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(3.dp))
                        .clickable { menuOpen = true }
                        .pointerInput(b.id) {
                            detectDragGestures(
                                onDragStart = { startY = it.y; dy = 0f; onDragStart() },
                                onDrag = { change, amount -> change.consume(); dy += amount.y; onDrag(startY + dy + 8f) },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragEnd,
                            )
                        },
                )
            }
            BlockMenu(state, b, menuOpen) { menuOpen = false }
        }
        Spacer(Modifier.width((b.indent * 24).dp))
        Box(Modifier.weight(1f)) {
            BlockContent(state, b, number, colors, pageTitle, onOpenPage)
        }
    }
}

private fun handleTop(type: BlockType) = when (type) {
    BlockType.Heading1 -> 10.dp
    BlockType.Heading2 -> 7.dp
    BlockType.Heading3 -> 5.dp
    BlockType.Code, BlockType.Callout -> 12.dp
    else -> 3.dp
}

@Composable
private fun BlockContent(
    state: EditorState,
    b: EditorBlock,
    number: Int,
    colors: NoteColors,
    pageTitle: (String) -> String?,
    onOpenPage: (String) -> Unit,
) {
    val body = MaterialTheme.typography.bodyLarge.copy(color = colors.text, fontSize = 16.sp, lineHeight = 1.5.em)
    when (b.type) {
        BlockType.Divider -> Box(Modifier.fillMaxWidth().height(26.dp), contentAlignment = Alignment.Center) {
            HorizontalDivider(color = colors.divider)
        }
        BlockType.Image -> ImageBlock(state, b, colors)
        BlockType.PageLink -> {
            val title = pageTitle(b.url)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable { if (title != null) onOpenPage(b.url) }.padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Outlined.Article, null, tint = colors.secondaryText, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    title ?: "${b.text.ifBlank { "페이지" }} (찾을 수 없음)",
                    style = body.copy(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium),
                    color = if (title != null) colors.text else colors.faint,
                )
            }
        }
        BlockType.Bulleted -> Marker(text = listOf("•", "◦", "▪")[b.indent % 3], body) { EditorField(state, b, body, colors) }
        BlockType.Numbered -> Marker(text = "$number.", body) { EditorField(state, b, body, colors) }
        BlockType.Todo -> Row {
            Box(Modifier.padding(top = 4.dp, end = 8.dp)) {
                TodoCheck(b.checked, colors) { state.setChecked(b, !b.checked) }
            }
            EditorField(
                state, b,
                if (b.checked) body.copy(color = colors.secondaryText, textDecoration = TextDecoration.LineThrough) else body,
                colors,
            )
        }
        BlockType.Toggle -> Row {
            val open = b.id in state.expanded
            Icon(
                if (open) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowRight,
                contentDescription = if (open) "접기" else "펼치기",
                tint = colors.text,
                modifier = Modifier.padding(top = 2.dp, end = 2.dp).size(22.dp).clip(RoundedCornerShape(3.dp)).clickable { state.toggleExpanded(b) },
            )
            EditorField(state, b, body, colors)
        }
        BlockType.Quote -> Row(Modifier.heightIn(min = 24.dp)) {
            Box(Modifier.padding(vertical = 2.dp).width(3.dp).height(24.dp).background(colors.text))
            Spacer(Modifier.width(12.dp))
            EditorField(state, b, body.copy(fontSize = 17.sp), colors)
        }
        BlockType.Callout -> Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(colors.calloutBg).padding(12.dp),
        ) {
            Icon(Icons.Outlined.Lightbulb, null, tint = Color(0xFFD9A400), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            EditorField(state, b, body, colors)
        }
        BlockType.Code -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(colors.codeBg).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = b.language,
                onValueChange = { state.setLanguage(b, it.take(20)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.labelSmall.copy(color = colors.secondaryText),
                cursorBrush = SolidColor(colors.text),
                decorationBox = { inner ->
                    Box { if (b.language.isEmpty()) Text("언어", style = MaterialTheme.typography.labelSmall, color = colors.faint); inner() }
                },
                modifier = Modifier.padding(bottom = 6.dp).widthIn(min = 40.dp),
            )
            EditorField(
                state, b,
                body.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 1.45.em),
                colors,
                inline = false,
            )
        }
        BlockType.Heading1 -> EditorField(state, b, body.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 1.25.em), colors)
        BlockType.Heading2 -> EditorField(state, b, body.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.3.em), colors)
        BlockType.Heading3 -> EditorField(state, b, body.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.3.em), colors)
        BlockType.Paragraph -> EditorField(state, b, body, colors)
    }
}

@Composable
private fun Marker(text: String, style: TextStyle, content: @Composable () -> Unit) {
    Row {
        Text(text, style = style, modifier = Modifier.widthIn(min = 22.dp).padding(end = 4.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun TodoCheck(checked: Boolean, colors: NoteColors, onToggle: () -> Unit) {
    Box(
        Modifier.size(16.dp).clip(RoundedCornerShape(3.dp))
            .background(if (checked) colors.accent else Color.Transparent)
            .border(1.5.dp, if (checked) colors.accent else colors.text, RoundedCornerShape(3.dp))
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Outlined.Check, "완료", tint = Color.White, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun ImageBlock(state: EditorState, b: EditorBlock, colors: NoteColors) {
    if (b.url.isBlank()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(colors.calloutBg)
                .clickable { state.pendingImageFor = b.id }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Image, null, tint = colors.secondaryText)
            Spacer(Modifier.width(8.dp))
            Text("이미지 주소 추가", color = colors.secondaryText)
        }
    } else {
        Column(Modifier.fillMaxWidth().clickable { state.pendingImageFor = b.id }) {
            AsyncImage(
                model = b.url,
                contentDescription = b.text,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp, max = 600.dp).clip(RoundedCornerShape(4.dp)),
            )
            if (b.text.isNotBlank()) Text(b.text, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun placeholderFor(type: BlockType) = when (type) {
    BlockType.Paragraph -> "글을 쓰거나 '/' 를 입력해 명령어 사용"
    BlockType.Heading1 -> "제목 1"
    BlockType.Heading2 -> "제목 2"
    BlockType.Heading3 -> "제목 3"
    BlockType.Bulleted, BlockType.Numbered -> "목록"
    BlockType.Todo -> "할 일"
    BlockType.Toggle -> "토글"
    BlockType.Quote -> "인용"
    BlockType.Callout -> "메모"
    BlockType.Code -> "코드"
    else -> ""
}

@Composable
private fun EditorField(
    state: EditorState,
    b: EditorBlock,
    style: TextStyle,
    colors: NoteColors,
    inline: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val focused = state.focusedId == b.id
    val transformation = remember(colors, inline) { if (inline) InlineMarkdownTransformation(colors) else VisualTransformation.None }

    LaunchedEffect(state.focusRequest) {
        val req = state.focusRequest ?: return@LaunchedEffect
        if (req.blockId != b.id) return@LaunchedEffect
        b.value = b.value.copy(selection = TextRange(req.cursor.coerceIn(0, b.text.length)))
        // 방금 추가된 블록은 한 프레임 뒤에야 포커스를 받을 수 있다.
        repeat(3) {
            if (runCatching { focusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }

    Box {
        BasicTextField(
            value = b.value,
            onValueChange = { state.onValueChange(b, it) },
            textStyle = style,
            cursorBrush = SolidColor(colors.text),
            visualTransformation = transformation,
            onTextLayout = { layout = it },
            modifier = Modifier.fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused) {
                        state.focusedId = b.id
                    } else if (state.focusedId == b.id && state.slash?.blockId == b.id) {
                        state.closeSlash()
                    }
                }
                .onPreviewKeyEvent { handleKey(it, state, b, layout) },
        )
        if (b.text.isEmpty() && (focused || (b.type != BlockType.Paragraph))) {
            Text(placeholderFor(b.type), style = style.copy(color = colors.faint, textDecoration = null))
        }
        SlashMenu(state, b, colors)
    }
}

private fun handleKey(e: KeyEvent, state: EditorState, b: EditorBlock, layout: TextLayoutResult?): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val mod = if (isApplePlatform) e.isMetaPressed else e.isCtrlPressed
    val slash = state.slash
    if (slash != null && slash.blockId == b.id) {
        when (e.key) {
            Key.DirectionDown -> { state.moveSlashSelection(1); return true }
            Key.DirectionUp -> { state.moveSlashSelection(-1); return true }
            Key.Enter, Key.NumPadEnter -> { state.applySlash(); return true }
            Key.Escape -> { state.closeSlash(); return true }
        }
    }
    val sel = b.value.selection
    return when {
        (e.key == Key.Enter || e.key == Key.NumPadEnter) && e.isShiftPressed -> { state.softBreak(b); true }
        e.key == Key.Tab -> { state.indent(b, if (e.isShiftPressed) -1 else 1); true }
        e.key == Key.Backspace -> state.backspaceAtStart(b)
        e.key == Key.Delete -> state.deleteAtEnd(b)
        mod && e.isShiftPressed && e.key == Key.DirectionUp -> { state.moveBy(b, -1); true }
        mod && e.isShiftPressed && e.key == Key.DirectionDown -> { state.moveBy(b, 1); true }
        e.key == Key.DirectionUp && !e.isShiftPressed && sel.collapsed &&
            (layout?.getLineForOffset(sel.start) ?: 0) == 0 -> state.focusPrevious(b)
        e.key == Key.DirectionDown && !e.isShiftPressed && sel.collapsed &&
            (layout == null || layout.getLineForOffset(sel.start) == layout.lineCount - 1) -> state.focusNext(b)
        e.key == Key.DirectionLeft && sel.collapsed && sel.start == 0 && !e.isShiftPressed -> state.focusPrevious(b)
        e.key == Key.DirectionRight && sel.collapsed && sel.start == b.text.length && !e.isShiftPressed -> {
            val visible = state.visibleBlocks()
            val next = visible.drop(visible.indexOfFirst { it.id == b.id } + 1).firstOrNull { it.type.hasText }
            if (next != null) { state.requestFocus(next.id, 0); true } else false
        }
        mod && e.key == Key.B -> { state.format(b, InlineFormat.Bold); true }
        mod && e.key == Key.I -> { state.format(b, InlineFormat.Italic); true }
        mod && e.key == Key.E -> { state.format(b, InlineFormat.Code); true }
        mod && e.isShiftPressed && e.key == Key.S -> { state.format(b, InlineFormat.Strike); true }
        mod && e.key == Key.D -> { state.duplicate(b); true }
        mod && e.key == Key.Enter && b.type == BlockType.Todo -> { state.setChecked(b, !b.checked); true }
        mod && e.key == Key.Enter && b.type == BlockType.Toggle -> { state.toggleExpanded(b); true }
        else -> false
    }
}

@Composable
private fun SlashMenu(state: EditorState, b: EditorBlock, colors: NoteColors) {
    val slash = state.slash
    val open = slash != null && slash.blockId == b.id
    val items = if (open) state.slashItems() else emptyList()
    DropdownMenu(
        expanded = open && items.isNotEmpty(),
        onDismissRequest = { state.closeSlash() },
        properties = PopupProperties(focusable = false),
        modifier = Modifier.heightIn(max = 360.dp),
    ) {
        Text("기본 블록", style = MaterialTheme.typography.labelSmall, color = colors.secondaryText, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        items.forEachIndexed { i, type ->
            DropdownMenuItem(
                text = { Text(type.label) },
                leadingIcon = { BlockTypeIcon(type, colors) },
                onClick = { state.applySlash(type) },
                modifier = Modifier.background(if (i == slash?.selected) colors.hover else Color.Transparent),
            )
        }
    }
}

@Composable
fun BlockTypeIcon(type: BlockType, colors: NoteColors) {
    val label = when (type) {
        BlockType.Paragraph -> "Aa"
        BlockType.Heading1 -> "H1"
        BlockType.Heading2 -> "H2"
        BlockType.Heading3 -> "H3"
        BlockType.Bulleted -> "•"
        BlockType.Numbered -> "1."
        BlockType.Todo -> "☑"
        BlockType.Toggle -> "▸"
        BlockType.Quote -> "❝"
        BlockType.Callout -> "!"
        BlockType.Code -> "</>"
        BlockType.Divider -> "—"
        BlockType.Image -> "🖼"
        BlockType.PageLink -> "↗"
    }
    Box(
        Modifier.size(28.dp).border(1.dp, colors.divider, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.text)
    }
}

@Composable
private fun BoxScope.BlockMenu(state: EditorState, b: EditorBlock, open: Boolean, onDismiss: () -> Unit) {
    var turnInto by remember { mutableStateOf(false) }
    DropdownMenu(expanded = open, onDismissRequest = { turnInto = false; onDismiss() }) {
        if (!turnInto) {
            MenuItem("삭제", Icons.Outlined.Delete) { state.delete(b); onDismiss() }
            MenuItem("복제", Icons.Outlined.ContentCopy) { state.duplicate(b); onDismiss() }
            MenuItem("위로 이동", Icons.Outlined.ArrowUpward) { state.moveBy(b, -1); onDismiss() }
            MenuItem("아래로 이동", Icons.Outlined.ArrowDownward) { state.moveBy(b, 1); onDismiss() }
            MenuItem("들여쓰기", Icons.AutoMirrored.Outlined.FormatIndentIncrease) { state.indent(b, 1); onDismiss() }
            MenuItem("내어쓰기", Icons.AutoMirrored.Outlined.FormatIndentDecrease) { state.indent(b, -1); onDismiss() }
            MenuItem("아래에 블록 추가", Icons.Outlined.Add) { state.insertAfter(b); onDismiss() }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("전환 ▸") }, onClick = { turnInto = true })
        } else {
            BlockType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label) },
                    leadingIcon = { BlockTypeIcon(type, noteColors) },
                    onClick = { state.turnInto(b, type); turnInto = false; onDismiss() },
                )
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}
