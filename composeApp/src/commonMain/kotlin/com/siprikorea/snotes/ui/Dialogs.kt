package com.siprikorea.snotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.siprikorea.snotes.AppGraph
import com.siprikorea.snotes.model.PageMeta
import com.siprikorea.snotes.sync.SyncStatus
import com.siprikorea.snotes.ui.theme.noteColors
import kotlinx.coroutines.launch

/** 검색창처럼 위쪽에 뜨는 카드형 대화상자. */
@Composable
private fun PaletteDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            tonalElevation = 2.dp,
            shadowElevation = 12.dp,
            modifier = Modifier.padding(16.dp).widthIn(max = 620.dp).fillMaxWidth().heightIn(max = 560.dp),
        ) { content() }
    }
}

@Composable
private fun PageResultRow(meta: PageMeta, subtitle: String?, path: String?, onClick: () -> Unit) {
    val colors = noteColors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageIcon(meta, 20)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meta.displayTitle, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (!path.isNullOrEmpty()) {
                    Text(" — $path", color = colors.faint, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = colors.secondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun SearchDialog(graph: AppGraph, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = noteColors
    var query by remember { mutableStateOf("") }
    val pages by graph.repo.pages.collectAsState()
    val results = remember(query, pages) {
        if (query.isBlank()) {
            pages.values.map { it.meta }.filter { !graph.repo.isInTrash(it.id) }.sortedByDescending { it.updated }.take(12)
                .map { it to null }
        } else {
            graph.repo.search(query).map { it.page to it.snippet }
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    PaletteDialog(onDismiss) {
        Column {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, tint = colors.secondaryText)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("페이지 검색…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let { onOpen(it.first.id) } }),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focus).onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                            results.firstOrNull()?.let { r -> onOpen(r.first.id) }
                            true
                        } else {
                            false
                        }
                    },
                )
            }
            HorizontalDivider(color = colors.divider)
            Text(
                if (query.isBlank()) "최근 페이지" else "검색 결과 ${results.size}개",
                style = MaterialTheme.typography.labelSmall, color = colors.secondaryText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn {
                items(results, key = { it.first.id }) { (meta, snippet) ->
                    PageResultRow(meta, snippet, graph.repo.ancestors(meta.id).joinToString(" / ") { it.displayTitle }) { onOpen(meta.id) }
                }
            }
            if (results.isEmpty()) {
                Text("결과가 없습니다", color = colors.faint, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
fun TrashDialog(graph: AppGraph, ui: AppUiState, onDismiss: () -> Unit) {
    val colors = noteColors
    val pages by graph.repo.pages.collectAsState()
    var filter by remember { mutableStateOf("") }
    var confirmEmpty by remember { mutableStateOf(false) }
    val items = remember(pages, filter) { graph.repo.trashRoots().filter { it.displayTitle.contains(filter, ignoreCase = true) } }

    PaletteDialog(onDismiss) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("휴지통", style = MaterialTheme.typography.titleMedium, color = colors.text, modifier = Modifier.weight(1f))
                if (items.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text("비우기", color = Color(0xFFE03E3E)) }
            }
            OutlinedTextField(
                value = filter, onValueChange = { filter = it }, singleLine = true,
                placeholder = { Text("휴지통에서 찾기") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (items.isEmpty()) Text("휴지통이 비어 있습니다", color = colors.faint, modifier = Modifier.padding(16.dp))
            LazyColumn {
                items(items, key = { it.id }) { m ->
                    Row(Modifier.fillMaxWidth().clickable { ui.open(m.id); onDismiss() }.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        PageIcon(m, 20)
                        Spacer(Modifier.width(12.dp))
                        Text(m.displayTitle, color = colors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { graph.scope.launch { graph.repo.restore(m.id) } }) {
                            Icon(Icons.AutoMirrored.Outlined.Undo, "복원", tint = colors.secondaryText)
                        }
                        IconButton(onClick = { graph.scope.launch { graph.repo.deleteForever(m.id); ui.ensureValid() } }) {
                            Icon(Icons.Outlined.DeleteForever, "영구 삭제", tint = colors.secondaryText)
                        }
                    }
                }
            }
        }
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("휴지통을 비울까요?") },
            text = { Text("페이지가 영구 삭제되고, 다음 동기화 때 Google Drive 에서도 휴지통으로 이동합니다.") },
            confirmButton = {
                TextButton(onClick = {
                    graph.scope.launch { graph.repo.emptyTrash(); ui.ensureValid() }
                    confirmEmpty = false
                }) { Text("영구 삭제", color = Color(0xFFE03E3E)) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("취소") } },
        )
    }
}

/** 트리 순서로 펼친 페이지 목록(휴지통 제외). [exclude] 와 그 하위는 뺀다. */
private fun flatTree(graph: AppGraph, exclude: String?): List<Pair<PageMeta, Int>> {
    val out = ArrayList<Pair<PageMeta, Int>>()
    fun walk(parent: String?, depth: Int) {
        for (m in graph.repo.children(parent)) {
            if (m.id == exclude) continue
            out += m to depth
            walk(m.id, depth + 1)
        }
    }
    walk(null, 0)
    return out
}

@Composable
fun MoveDialog(graph: AppGraph, pageId: String, onDismiss: () -> Unit) {
    val colors = noteColors
    var filter by remember { mutableStateOf("") }
    val all = remember(pageId) { flatTree(graph, pageId) }
    val shown = all.filter { filter.isBlank() || it.first.displayTitle.contains(filter, ignoreCase = true) }
    fun moveTo(parent: String?) {
        graph.scope.launch { graph.repo.move(pageId, parent, Int.MAX_VALUE) }
        onDismiss()
    }
    PaletteDialog(onDismiss) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text("옮길 위치", style = MaterialTheme.typography.titleMedium, color = colors.text, modifier = Modifier.padding(horizontal = 16.dp))
            OutlinedTextField(
                value = filter, onValueChange = { filter = it }, singleLine = true, placeholder = { Text("페이지 찾기") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn {
                item {
                    Text("최상위로 옮기기", color = colors.accent, modifier = Modifier.fillMaxWidth().clickable { moveTo(null) }.padding(16.dp, 10.dp))
                }
                items(shown, key = { it.first.id }) { (m, depth) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { moveTo(m.id) }.padding(start = (16 + depth * 16).dp, top = 8.dp, bottom = 8.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PageIcon(m)
                        Spacer(Modifier.width(10.dp))
                        Text(m.displayTitle, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun PageLinkDialog(
    graph: AppGraph,
    excludeId: String,
    onDismiss: () -> Unit,
    onCreateChild: () -> Unit,
    onPick: (PageMeta) -> Unit,
) {
    val colors = noteColors
    var filter by remember { mutableStateOf("") }
    val all = remember { flatTree(graph, null).map { it.first }.filter { it.id != excludeId } }
    val shown = all.filter { filter.isBlank() || it.displayTitle.contains(filter, ignoreCase = true) }
    PaletteDialog(onDismiss) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text("페이지 링크", style = MaterialTheme.typography.titleMedium, color = colors.text, modifier = Modifier.padding(horizontal = 16.dp))
            OutlinedTextField(
                value = filter, onValueChange = { filter = it }, singleLine = true, placeholder = { Text("연결할 페이지 찾기") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onCreateChild).padding(16.dp, 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Add, null, tint = colors.accent)
                Spacer(Modifier.width(10.dp))
                Text("새 하위 페이지 만들기", color = colors.accent)
            }
            LazyColumn {
                items(shown, key = { it.id }) { m ->
                    PageResultRow(m, null, graph.repo.ancestors(m.id).joinToString(" / ") { it.displayTitle }) { onPick(m) }
                }
            }
        }
    }
}

@Composable
fun UrlDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("URL") }) },
        confirmButton = { TextButton(enabled = url.isNotBlank(), onClick = { onConfirm(url.trim()) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
fun ImageDialog(initialUrl: String, initialCaption: String, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var url by remember { mutableStateOf(initialUrl) }
    var caption by remember { mutableStateOf(initialCaption) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이미지") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("이미지 주소 (https://…)") })
                OutlinedTextField(caption, { caption = it }, singleLine = true, label = { Text("설명 (선택)") })
            }
        },
        confirmButton = { TextButton(enabled = url.isNotBlank(), onClick = { onConfirm(url.trim(), caption.trim()) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private val EMOJIS = listOf(
    "📝", "📄", "📒", "📓", "📔", "📕", "📗", "📘", "📙", "📚", "🗂️", "📁", "📌", "📎", "🔖", "🏷️",
    "✅", "☑️", "⭐", "🌟", "💡", "🔥", "🎯", "🚀", "🧠", "💬", "📣", "🔔", "🗓️", "⏰", "📅", "🧭",
    "💼", "🏠", "🏢", "🎓", "🧪", "🔬", "💻", "🖥️", "📱", "⚙️", "🛠️", "🔧", "🧩", "🎨", "🎵", "🎬",
    "📷", "✈️", "🌍", "🗺️", "🍎", "☕", "🍳", "🏃", "💪", "❤️", "😀", "😎", "🤔", "🙏", "🎉", "🌱",
    "🌸", "🌙", "☀️", "⚡", "💰", "📈", "📊", "🧾", "🔒", "🔑", "🐶", "🐱", "✏️", "🖊️", "📐", "🧮",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmojiPickerDialog(onDismiss: () -> Unit, onRemove: (() -> Unit)?, onPick: (String) -> Unit) {
    val colors = noteColors
    var custom by remember { mutableStateOf("") }
    PaletteDialog(onDismiss) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("아이콘", style = MaterialTheme.typography.titleMedium, color = colors.text, modifier = Modifier.weight(1f))
                if (onRemove != null) TextButton(onClick = onRemove) { Text("제거") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                EMOJIS.forEach { e ->
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).clickable { onPick(e) },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 24.sp) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    custom, { custom = it.take(8) }, singleLine = true,
                    label = { Text("직접 입력") }, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(enabled = custom.isNotBlank(), onClick = { onPick(custom.trim()) }) { Text("적용") }
            }
        }
    }
}

@Composable
fun MarkdownDialog(graph: AppGraph, pageId: String, onDismiss: () -> Unit) {
    val colors = noteColors
    val text = graph.repo.storedText(pageId).orEmpty()
    PaletteDialog(onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Markdown", style = MaterialTheme.typography.titleMedium, color = colors.text, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
            Text(
                "Google Drive 의 S Notes 폴더에 이 내용 그대로 저장됩니다.",
                style = MaterialTheme.typography.bodySmall, color = colors.secondaryText,
            )
            Spacer(Modifier.height(8.dp))
            SelectionContainer(
                Modifier.weight(1f, fill = false).clip(RoundedCornerShape(6.dp)).background(colors.codeBg)
                    .verticalScroll(rememberScrollState()).padding(12.dp),
            ) {
                Text(text, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = colors.text)
            }
        }
    }
}

@Composable
fun SettingsDialog(graph: AppGraph, onDismiss: () -> Unit) {
    val colors = noteColors
    val status by graph.sync.status.collectAsState()
    val account by graph.sync.account.collectAsState()
    val reason = graph.sync.auth.unavailableReason

    PaletteDialog(onDismiss) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("설정 및 동기화", style = MaterialTheme.typography.titleLarge, color = colors.text, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
            Text("Google Drive 동기화", style = MaterialTheme.typography.titleSmall, color = colors.text)
            Text(
                "모든 페이지가 내 Google Drive 의 \"S Notes\" 폴더에 Markdown(.md) 파일로 저장됩니다. " +
                    "하위 페이지는 같은 이름의 폴더 안에 들어갑니다. 앱은 자신이 만든 파일에만 접근합니다(drive.file 권한).",
                style = MaterialTheme.typography.bodySmall, color = colors.secondaryText,
            )
            when {
                reason != null -> Text(reason, color = Color(0xFFE03E3E), style = MaterialTheme.typography.bodySmall)
                status is SyncStatus.SignedOut -> Button(onClick = { graph.sync.signIn() }) { Text("Google 계정으로 로그인") }
                else -> {
                    Text("로그인: ${account ?: "Google 계정"}", color = colors.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { graph.sync.requestSync() }, enabled = status !is SyncStatus.Syncing) { Text("지금 동기화") }
                        OutlinedButton(onClick = { graph.sync.signOut() }) { Text("로그아웃") }
                    }
                }
            }
            val statusText = when (val s = status) {
                SyncStatus.SignedOut -> "동기화 꺼짐 — 이 기기에만 저장됩니다."
                SyncStatus.Idle -> "대기 중"
                SyncStatus.Syncing -> "동기화 중…"
                is SyncStatus.Done -> "마지막 동기화: ${s.at.toString().take(19).replace('T', ' ')} UTC · " +
                    "받음 ${s.report.pulled}, 보냄 ${s.report.pushed}, 삭제 ${s.report.deleted}" +
                    if (s.report.conflicts > 0) ", 충돌 ${s.report.conflicts}(사본으로 보관)" else ""
                is SyncStatus.Failed -> "오류: ${s.message}"
            }
            Text(statusText, style = MaterialTheme.typography.bodySmall, color = if (status is SyncStatus.Failed) Color(0xFFE03E3E) else colors.secondaryText)
            HorizontalDivider(color = colors.divider)
            Text("단축키", style = MaterialTheme.typography.titleSmall, color = colors.text)
            Text(
                """
                / 블록 메뉴 · Enter 새 블록 · Shift+Enter 줄바꿈 · Tab / Shift+Tab 들여쓰기
                # ## ### 제목 · - 목록 · 1. 번호 · [] 할 일 · > 토글 · " 인용 · ``` 코드 · --- 구분선
                Ctrl/⌘ + B 굵게 · I 기울임 · E 코드 · Shift+S 취소선 · D 블록 복제
                Ctrl/⌘ + Shift + ↑/↓ 블록 이동 · Ctrl/⌘ + K 검색 · Ctrl/⌘ + N 새 페이지
                """.trimIndent(),
                style = MaterialTheme.typography.bodySmall, color = colors.secondaryText,
            )
        }
    }
}
