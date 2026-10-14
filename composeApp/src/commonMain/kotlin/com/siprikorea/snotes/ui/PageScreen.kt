package com.siprikorea.snotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siprikorea.snotes.AppGraph
import com.siprikorea.snotes.model.BlockType
import com.siprikorea.snotes.model.PageMeta
import com.siprikorea.snotes.sync.SyncStatus
import com.siprikorea.snotes.ui.editor.BlockEditor
import com.siprikorea.snotes.ui.editor.CenteredColumn
import com.siprikorea.snotes.ui.editor.EditorState
import com.siprikorea.snotes.ui.editor.EditorToolbar
import com.siprikorea.snotes.ui.editor.HEADER_KEY
import com.siprikorea.snotes.ui.theme.noteColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PageScreen(
    graph: AppGraph,
    ui: AppUiState,
    pageId: String,
    showMenuButton: Boolean,
    onMenu: () -> Unit,
    compact: Boolean,
) {
    val colors = noteColors
    val pages by graph.repo.pages.collectAsState()
    val page = pages[pageId] ?: return
    val repo = graph.repo

    val editor = remember(pageId) {
        EditorState(pageId, page.blocks, graph.scope) { blocks ->
            // 페이지 링크 블록에는 현재 제목을 함께 저장해 Markdown 만 봐도 알 수 있게 한다.
            repo.updateBlocks(
                pageId,
                blocks.map { b -> if (b.type == BlockType.PageLink) b.copy(text = repo[b.url]?.meta?.title ?: b.text) else b },
            )
        }
    }
    LaunchedEffect(page.blocks) { editor.replaceFromRepository(page.blocks) }
    DisposableEffect(pageId) {
        onDispose { if (editor.hasPendingSave) graph.scope.launch { editor.flushNow() } }
    }

    var linkDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val pad: Dp = if (compact) 12.dp else 48.dp

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(graph, ui, page.meta, showMenuButton, onMenu)
        if (repo.isInTrash(pageId)) TrashBanner(graph, ui, pageId)
        BlockEditor(
            state = editor,
            pageTitle = { id -> repo[id]?.meta?.displayTitle },
            onOpenPage = ui::open,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = pad,
        ) {
            item(key = HEADER_KEY) {
                CenteredColumn(pad + 24.dp) { PageHeader(graph, page.meta, editor, compact) }
            }
        }
        if (editor.focusedId != null) {
            EditorToolbar(
                editor,
                onLink = { linkDialog = true },
                onDone = { focusManager.clearFocus(); editor.focusedId = null },
                modifier = Modifier.imePadding(),
            )
        }
    }

    if (linkDialog) {
        UrlDialog(title = "링크 추가", initial = "https://", onDismiss = { linkDialog = false }) { url ->
            editor.focused?.let { editor.link(it, url) }
            linkDialog = false
        }
    }
    editor.pendingImageFor?.let { id ->
        val b = editor.blocks.firstOrNull { it.id == id }
        ImageDialog(
            initialUrl = b?.url.orEmpty(),
            initialCaption = b?.text.orEmpty(),
            onDismiss = {
                if (b != null && b.url.isBlank()) editor.delete(b)
                editor.pendingImageFor = null
            },
        ) { url, caption ->
            if (b != null) editor.setUrl(b, url, caption)
            editor.pendingImageFor = null
        }
    }
    editor.pendingLinkFor?.let { id ->
        val b = editor.blocks.firstOrNull { it.id == id }
        PageLinkDialog(
            graph = graph,
            excludeId = pageId,
            onDismiss = {
                if (b != null && b.url.isBlank()) editor.delete(b)
                editor.pendingLinkFor = null
            },
            onCreateChild = {
                graph.scope.launch {
                    val child = repo.create(pageId)
                    if (b != null) editor.setUrl(b, child.id, "")
                    editor.pendingLinkFor = null
                    editor.flushNow()
                    ui.open(child.id)
                }
            },
        ) { target ->
            if (b != null) editor.setUrl(b, target.id, target.title)
            editor.pendingLinkFor = null
        }
    }
}

@Composable
private fun TopBar(graph: AppGraph, ui: AppUiState, meta: PageMeta, showMenuButton: Boolean, onMenu: () -> Unit) {
    val colors = noteColors
    val status by graph.sync.status.collectAsState()
    var menu by remember { mutableStateOf(false) }
    val crumbs = graph.repo.ancestors(meta.id) + meta

    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showMenuButton) {
            IconButton(onClick = onMenu) { Icon(Icons.Outlined.Menu, "메뉴", tint = colors.secondaryText) }
        }
        if (ui.canGoBack) {
            IconButton(onClick = { ui.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로", tint = colors.secondaryText) }
        }
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            crumbs.forEachIndexed { i, c ->
                if (i > 0) Text("/", color = colors.faint, modifier = Modifier.padding(horizontal = 2.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(4.dp)).clickable { ui.open(c.id) }.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (c.icon.isNotEmpty()) Text(c.icon, fontSize = 13.sp, modifier = Modifier.padding(end = 4.dp))
                    Text(
                        c.displayTitle, style = MaterialTheme.typography.bodyMedium, color = colors.text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp),
                    )
                }
            }
        }
        SyncIndicator(status) { ui.showSettings = true }
        IconButton(onClick = { graph.scope.launch { graph.repo.toggleFavorite(meta.id) } }) {
            Icon(
                if (meta.favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                if (meta.favorite) "즐겨찾기 해제" else "즐겨찾기",
                tint = if (meta.favorite) Color(0xFFF2C94C) else colors.secondaryText,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, "더 보기", tint = colors.secondaryText) }
            PageMenu(graph, ui, meta, menu) { menu = false }
        }
    }
}

@Composable
private fun SyncIndicator(status: SyncStatus, onClick: () -> Unit) {
    val colors = noteColors
    val (icon, desc, tint) = when (status) {
        SyncStatus.SignedOut -> Triple(Icons.Outlined.CloudOff, "동기화 꺼짐", colors.faint)
        SyncStatus.Idle, is SyncStatus.Done -> Triple(Icons.Outlined.CloudDone, "동기화됨", colors.secondaryText)
        SyncStatus.Syncing -> Triple(Icons.Outlined.CloudSync, "동기화 중", colors.accent)
        is SyncStatus.Failed -> Triple(Icons.Outlined.ErrorOutline, "동기화 오류", Color(0xFFE03E3E))
    }
    IconButton(onClick = onClick) { Icon(icon, desc, tint = tint) }
}

@Composable
private fun TrashBanner(graph: AppGraph, ui: AppUiState, pageId: String) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFEB5757)).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("이 페이지는 휴지통에 있습니다.", color = Color.White, modifier = Modifier.weight(1f))
        TextButton(onClick = {
            graph.scope.launch {
                val root = (graph.repo.ancestors(pageId) + graph.repo[pageId]!!.meta).first { it.trashed }
                graph.repo.restore(root.id)
            }
        }) { Text("복원", color = Color.White) }
        TextButton(onClick = {
            graph.scope.launch {
                val root = (graph.repo.ancestors(pageId) + graph.repo[pageId]!!.meta).first { it.trashed }
                graph.repo.deleteForever(root.id)
                ui.ensureValid()
            }
        }) { Text("영구 삭제", color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun PageHeader(graph: AppGraph, meta: PageMeta, editor: EditorState, compact: Boolean) {
    val colors = noteColors
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var emojiPicker by remember { mutableStateOf(false) }
    var title by remember(meta.id) { mutableStateOf(meta.title) }
    var titleFocused by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }

    // 제목 저장(디바운스) / 다른 기기에서 바뀐 제목 반영
    LaunchedEffect(title) {
        if (title == meta.title) return@LaunchedEffect
        delay(300)
        graph.repo.setTitle(meta.id, title)
    }
    LaunchedEffect(meta.title) { if (!titleFocused) title = meta.title }
    // 새로 만든 빈 페이지는 제목부터 입력
    LaunchedEffect(meta.id) {
        if (meta.title.isEmpty() && graph.repo[meta.id]?.let { com.siprikorea.snotes.store.PageRepository.isEmptyPage(it) } == true) {
            runCatching { titleFocus.requestFocus() }
        }
    }

    Column(Modifier.fillMaxWidth().hoverable(hover).padding(top = if (compact) 16.dp else 64.dp, bottom = 8.dp)) {
        if (meta.icon.isNotEmpty()) {
            Text(
                meta.icon, fontSize = 64.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { emojiPicker = true }.padding(4.dp),
            )
        }
        Box(Modifier.height(28.dp)) {
            if (meta.icon.isEmpty() && (hovered || compact || title.isEmpty())) {
                TextButton(onClick = { emojiPicker = true }) {
                    Text("☺ 아이콘 추가", color = colors.secondaryText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        val style = MaterialTheme.typography.headlineLarge.copy(
            color = colors.text, fontWeight = FontWeight.Bold, fontSize = if (compact) 30.sp else 40.sp,
        )
        Box {
            BasicTextField(
                value = title,
                onValueChange = { v ->
                    if ('\n' in v) {
                        title = v.replace("\n", "")
                        editor.focusFirst()
                    } else {
                        title = v
                    }
                },
                textStyle = style,
                cursorBrush = SolidColor(colors.text),
                modifier = Modifier.fillMaxWidth().focusRequester(titleFocus)
                    .onFocusChanged { titleFocused = it.isFocused }
                    .onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown) { editor.focusFirst(); true } else false
                    },
            )
            if (title.isEmpty()) Text(PageMeta.UNTITLED, style = style.copy(color = colors.faint))
        }
        Spacer(Modifier.height(12.dp))
    }

    if (emojiPicker) {
        EmojiPickerDialog(
            onDismiss = { emojiPicker = false },
            onRemove = if (meta.icon.isNotEmpty()) ({ graph.scope.launch { graph.repo.setIcon(meta.id, "") }; emojiPicker = false }) else null,
        ) { emoji ->
            graph.scope.launch { graph.repo.setIcon(meta.id, emoji) }
            emojiPicker = false
        }
    }
}

@Composable
fun EmptyState(ui: AppUiState, showMenuButton: Boolean, onMenu: () -> Unit) {
    val colors = noteColors
    Column(Modifier.fillMaxSize()) {
        if (showMenuButton) {
            IconButton(onClick = onMenu, modifier = Modifier.padding(4.dp)) { Icon(Icons.Outlined.Menu, "메뉴", tint = colors.secondaryText) }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("페이지를 선택하거나 새로 만드세요", color = colors.secondaryText)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { ui.newPage(null) }) { Text("+ 새 페이지") }
            }
        }
    }
}
