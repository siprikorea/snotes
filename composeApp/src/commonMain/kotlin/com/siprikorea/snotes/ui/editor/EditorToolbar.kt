package com.siprikorea.snotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.siprikorea.snotes.model.BlockType
import com.siprikorea.snotes.ui.theme.noteColors

/**
 * 블록을 편집 중일 때 화면 아래(키보드 위)에 뜨는 도구 모음.
 * 버튼이 포커스를 가져가지 않아 텍스트 선택이 유지된다.
 */
@Composable
fun EditorToolbar(state: EditorState, onLink: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val b = state.focused ?: return
    val colors = noteColors
    Surface(modifier.fillMaxWidth(), color = colors.sidebar) {
        androidx.compose.foundation.layout.Column {
            HorizontalDivider(color = colors.divider)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolButton(Icons.Outlined.Add, "블록 추가") { state.openSlash(b) }
                ToolButton(Icons.Outlined.FormatBold, "굵게") { state.format(b, InlineFormat.Bold) }
                ToolButton(Icons.Outlined.FormatItalic, "기울임") { state.format(b, InlineFormat.Italic) }
                ToolButton(Icons.Outlined.FormatStrikethrough, "취소선") { state.format(b, InlineFormat.Strike) }
                ToolButton(Icons.Outlined.Code, "코드") { state.format(b, InlineFormat.Code) }
                ToolButton(Icons.Outlined.Link, "링크", onLink)
                ToolButton(Icons.Outlined.CheckBox, "할 일") {
                    state.turnInto(b, if (b.type == BlockType.Todo) BlockType.Paragraph else BlockType.Todo)
                }
                ToolButton(Icons.AutoMirrored.Outlined.FormatIndentDecrease, "내어쓰기") { state.indent(b, -1) }
                ToolButton(Icons.AutoMirrored.Outlined.FormatIndentIncrease, "들여쓰기") { state.indent(b, 1) }
                ToolButton(Icons.Outlined.KeyboardHide, "편집 끝내기", onDone)
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.focusProperties { canFocus = false }) {
        Icon(icon, description, tint = noteColors.text, modifier = Modifier.size(22.dp).background(androidx.compose.ui.graphics.Color.Transparent))
    }
}
