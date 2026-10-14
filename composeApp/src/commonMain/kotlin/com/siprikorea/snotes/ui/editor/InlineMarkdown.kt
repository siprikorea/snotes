package com.siprikorea.snotes.ui.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import com.siprikorea.snotes.ui.theme.NoteColors

/**
 * 인라인 Markdown(**굵게**, *기울임*, ~~취소선~~, `코드`, [링크](url))을 편집 중에도 스타일로 보여 준다.
 * 텍스트는 바꾸지 않고(오프셋 매핑이 항등) 기호만 흐리게 표시하므로 커서 위치가 정확하다.
 */
class InlineMarkdownTransformation(private val colors: NoteColors) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(styleInline(text.text, colors), OffsetMapping.Identity)
}

private data class Rule(val regex: Regex, val markerLen: Int, val style: (NoteColors) -> SpanStyle)

private val RULES = listOf(
    Rule(Regex("""`[^`\n]+`"""), 1) { SpanStyle(fontFamily = FontFamily.Monospace, color = it.inlineCode, background = it.codeBg, fontSize = 0.9.em) },
    Rule(Regex("""\*\*[^*\n]+?\*\*"""), 2) { SpanStyle(fontWeight = FontWeight.Bold) },
    Rule(Regex("""(?<![*\w])\*[^*\s][^*\n]*?\*(?![*\w])"""), 1) { SpanStyle(fontStyle = FontStyle.Italic) },
    Rule(Regex("""(?<![_\w])_[^_\s][^_\n]*?_(?![_\w])"""), 1) { SpanStyle(fontStyle = FontStyle.Italic) },
    Rule(Regex("""~~[^~\n]+?~~"""), 2) { SpanStyle(textDecoration = TextDecoration.LineThrough) },
)

private val LINK = Regex("""\[([^\]\n]+)\]\(([^)\s]+)\)""")

fun styleInline(text: String, colors: NoteColors): AnnotatedString = AnnotatedString.Builder(text).apply {
    val marker = SpanStyle(color = colors.faint)
    val code = mutableListOf<IntRange>()
    RULES.forEachIndexed { i, rule ->
        rule.regex.findAll(text).forEach { m ->
            // 코드 안의 기호는 스타일하지 않는다.
            if (i > 0 && code.any { m.range.first in it }) return@forEach
            if (i == 0) code += m.range
            addStyle(rule.style(colors), m.range.first, m.range.last + 1)
            addStyle(marker, m.range.first, m.range.first + rule.markerLen)
            addStyle(marker, m.range.last + 1 - rule.markerLen, m.range.last + 1)
        }
    }
    LINK.findAll(text).forEach { m ->
        if (code.any { m.range.first in it }) return@forEach
        val label = m.groups[1]!!.range
        addStyle(SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline), label.first, label.last + 1)
        addStyle(marker, m.range.first, label.first)
        addStyle(marker.copy(color = colors.faint.copy(alpha = 0.7f)), label.last + 1, m.range.last + 1)
    }
}.toAnnotatedString()

enum class InlineFormat(val marker: String) { Bold("**"), Italic("*"), Strike("~~"), Code("`") }

/** 선택 영역을 마커로 감싸거나, 이미 감싸져 있으면 벗긴다. */
fun TextFieldValue.toggleFormat(format: InlineFormat): TextFieldValue {
    val m = format.marker
    val s = selection.min
    val e = selection.max
    val t = text
    if (s >= m.length && e + m.length <= t.length && t.substring(s - m.length, s) == m && t.substring(e, e + m.length) == m) {
        val nt = t.removeRange(e, e + m.length).removeRange(s - m.length, s)
        return copy(text = nt, selection = TextRange(s - m.length, e - m.length))
    }
    val nt = t.substring(0, s) + m + t.substring(s, e) + m + t.substring(e)
    return copy(text = nt, selection = if (s == e) TextRange(s + m.length) else TextRange(s + m.length, e + m.length))
}

fun TextFieldValue.insertLink(url: String): TextFieldValue {
    val s = selection.min
    val e = selection.max
    val label = text.substring(s, e).ifEmpty { url }
    val link = "[$label]($url)"
    return copy(text = text.substring(0, s) + link + text.substring(e), selection = TextRange(s + link.length))
}
