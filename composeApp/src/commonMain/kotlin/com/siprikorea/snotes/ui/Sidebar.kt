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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siprikorea.snotes.AppGraph
import com.siprikorea.snotes.isApplePlatform
import com.siprikorea.snotes.model.PageMeta
import com.siprikorea.snotes.ui.theme.noteColors
import kotlinx.coroutines.launch

@Composable
fun Sidebar(graph: AppGraph, ui: AppUiState, showCollapse: Boolean, modifier: Modifier = Modifier) {
    val colors = noteColors
    val pages by graph.repo.pages.collectAsState()
    val account by graph.sync.account.collectAsState()
    val roots = remember(pages) { graph.repo.children(null) }
    val favorites = remember(pages) { graph.repo.favorites() }
    val mod = if (isApplePlatform) "⌘" else "Ctrl+"

    Column(modifier.fillMaxHeight().background(colors.sidebar)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).clickable { ui.showSettings = true }.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).background(colors.text),
                    contentAlignment = Alignment.Center,
                ) {
                    Text((account?.firstOrNull() ?: 'S').uppercase(), color = colors.sidebar, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("S Notes", style = MaterialTheme.typography.titleSmall, color = colors.text, fontWeight = FontWeight.SemiBold)
                    if (account != null) {
                        Text(account!!, style = MaterialTheme.typography.labelSmall, color = colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (showCollapse) {
                IconButton(onClick = { ui.sidebarVisible = false }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "사이드바 닫기", tint = colors.secondaryText)
                }
            }
        }

        NavItem(Icons.Outlined.Search, "검색", hint = "${mod}K") { ui.showSearch = true }
        NavItem(Icons.Outlined.Settings, "설정 및 동기화") { ui.showSettings = true }
        NavItem(Icons.Outlined.Delete, "휴지통") { ui.showTrash = true }
        Spacer(Modifier.height(10.dp))

        LazyColumn(Modifier.weight(1f)) {
            if (favorites.isNotEmpty()) {
                item { SectionLabel("즐겨찾기") }
                items(favorites, key = { "fav-" + it.id }) { m ->
                    TreeRow(graph, ui, m, depth = 0, expandable = false)
                }
                item { Spacer(Modifier.height(10.dp)) }
            }
            item {
                SectionLabel("페이지", action = { ui.newPage(null) })
            }
            tree(graph, ui, roots, depth = 0)
            if (roots.isEmpty()) {
                item {
                    Text("페이지가 없습니다", color = colors.faint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                }
            }
        }
        NavItem(Icons.Outlined.Add, "새 페이지", hint = "${mod}N") { ui.newPage(null) }
        Spacer(Modifier.height(8.dp))
    }
}

private fun LazyListScope.tree(graph: AppGraph, ui: AppUiState, nodes: List<PageMeta>, depth: Int) {
    for (m in nodes) {
        item(key = m.id) { TreeRow(graph, ui, m, depth, expandable = true) }
        if (m.id in ui.expanded) {
            val children = graph.repo.children(m.id)
            if (children.isEmpty()) {
                item(key = m.id + "-empty") {
                    Text(
                        "하위 페이지 없음",
                        color = noteColors.faint,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = (40 + (depth + 1) * 14).dp, top = 4.dp, bottom = 4.dp),
                    )
                }
            } else {
                tree(graph, ui, children, depth + 1)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, action: (() -> Unit)? = null) {
    val colors = noteColors
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = colors.secondaryText, modifier = Modifier.weight(1f))
        if (action != null) {
            Icon(
                Icons.Outlined.Add, "새 페이지", tint = colors.secondaryText,
                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).clickable(onClick = action).padding(3.dp),
            )
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, hint: String? = null, onClick: () -> Unit) {
    val colors = noteColors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.secondaryText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = colors.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (hint != null) Text(hint, color = colors.faint, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun PageIcon(meta: PageMeta, size: Int = 18) {
    if (meta.icon.isNotEmpty()) {
        Text(meta.icon, fontSize = (size - 3).sp, modifier = Modifier.width(size.dp))
    } else {
        Icon(Icons.Outlined.Description, null, tint = noteColors.secondaryText, modifier = Modifier.size(size.dp))
    }
}

@Composable
private fun TreeRow(graph: AppGraph, ui: AppUiState, m: PageMeta, depth: Int, expandable: Boolean) {
    val colors = noteColors
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var menu by remember { mutableStateOf(false) }
    val selected = ui.currentPageId == m.id
    val open = m.id in ui.expanded

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(6.dp))
            .background(if (selected) colors.selected else Color.Transparent)
            .hoverable(hover)
            .clickable { ui.open(m.id) }
            .padding(start = (4 + depth * 14).dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (expandable) {
            Icon(
                if (open) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowRight,
                if (open) "접기" else "펼치기",
                tint = colors.faint,
                modifier = Modifier.size(22.dp).clip(CircleShape).clickable { ui.toggleExpanded(m.id) }.padding(2.dp),
            )
        } else {
            Spacer(Modifier.width(22.dp))
        }
        Spacer(Modifier.width(2.dp))
        PageIcon(m)
        Spacer(Modifier.width(8.dp))
        Text(
            m.displayTitle,
            color = if (m.title.isBlank()) colors.secondaryText else colors.text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
        )
        // 터치 기기에는 hover 가 없으므로 선택된 항목에는 항상 버튼을 보여 준다.
        if (hovered || selected || menu) {
            Box {
                Icon(
                    Icons.Outlined.MoreHoriz, "페이지 메뉴", tint = colors.secondaryText,
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).clickable { menu = true }.padding(3.dp),
                )
                PageMenu(graph, ui, m, menu) { menu = false }
            }
            Icon(
                Icons.Outlined.Add, "하위 페이지 추가", tint = colors.secondaryText,
                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).clickable { ui.newPage(m.id) }.padding(3.dp),
            )
        }
    }
}

/** 사이드바와 페이지 상단 ⋯ 메뉴가 함께 쓰는 페이지 메뉴. */
@Composable
fun PageMenu(graph: AppGraph, ui: AppUiState, m: PageMeta, expanded: Boolean, onDismiss: () -> Unit) {
    val scope = graph.scope
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (m.favorite) "즐겨찾기에서 제거" else "즐겨찾기에 추가") },
            leadingIcon = { Icon(if (m.favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline, null) },
            onClick = { scope.launch { graph.repo.toggleFavorite(m.id) }; onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("하위 페이지 추가") },
            leadingIcon = { Icon(Icons.Outlined.Add, null) },
            onClick = { ui.newPage(m.id); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("복제") },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
            onClick = { scope.launch { graph.repo.duplicate(m.id)?.let { ui.open(it.id) } }; onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("옮기기") },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
            onClick = { ui.movePageId = m.id; onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("Markdown 보기") },
            leadingIcon = { Icon(Icons.Outlined.Description, null) },
            onClick = { ui.markdownPageId = m.id; onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("휴지통으로 이동") },
            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
            onClick = { scope.launch { graph.repo.trash(m.id) }; onDismiss() },
        )
    }
}
