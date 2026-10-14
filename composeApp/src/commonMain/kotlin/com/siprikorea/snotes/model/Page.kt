package com.siprikorea.snotes.model

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** 페이지 메타데이터. Markdown 파일의 frontmatter 와 1:1 로 대응한다. */
data class PageMeta(
    val id: String,
    val title: String = "",
    val icon: String = "",
    val parentId: String? = null,
    val order: Double = 0.0,
    val favorite: Boolean = false,
    val trashed: Boolean = false,
    val created: Instant = Clock.System.now(),
    val updated: Instant = created,
    /** 우리가 모르는 frontmatter 키. 다른 도구로 편집한 파일을 잃지 않도록 보존한다. */
    val extra: Map<String, String> = emptyMap(),
) {
    val displayTitle: String get() = title.ifBlank { UNTITLED }

    companion object {
        const val UNTITLED = "제목 없음"
    }
}

data class Page(
    val meta: PageMeta,
    val blocks: List<Block> = listOf(Block()),
) {
    val id: String get() = meta.id
}

enum class BlockType(val label: String, val keywords: String) {
    Paragraph("텍스트", "text paragraph 텍스트"),
    Heading1("제목 1", "h1 heading 제목"),
    Heading2("제목 2", "h2 heading 제목"),
    Heading3("제목 3", "h3 heading 제목"),
    Bulleted("글머리 기호 목록", "bullet list ul 목록"),
    Numbered("번호 매기기 목록", "number list ol 번호"),
    Todo("할 일 목록", "todo checkbox task 할일 체크"),
    Toggle("토글 목록", "toggle details 토글"),
    Quote("인용", "quote 인용"),
    Callout("콜아웃", "callout note 콜아웃"),
    Code("코드", "code 코드"),
    Divider("구분선", "divider hr line 구분선"),
    Image("이미지", "image picture 이미지"),
    PageLink("페이지 링크", "page link 링크 페이지"),
    ;

    /** 텍스트 입력을 받는 블록인지. */
    val hasText: Boolean get() = this != Divider && this != Image && this != PageLink

    /** 같은 들여쓰기 목록으로 Enter 시 같은 타입을 이어가는 블록. */
    val continuesOnEnter: Boolean get() = this == Bulleted || this == Numbered || this == Todo || this == Toggle
}

/**
 * Notion 처럼 블록은 평평한 리스트로 두고 [indent] 로 중첩을 표현한다.
 * 블록 i 의 자식은 i 뒤에 이어지는 indent 가 더 큰 블록들이다.
 *
 * [url]: Image 는 이미지 주소, PageLink 는 대상 페이지 id.
 * [language]: Code 블록 언어.
 */
data class Block(
    val id: String = newId(),
    val type: BlockType = BlockType.Paragraph,
    val text: String = "",
    val indent: Int = 0,
    val checked: Boolean = false,
    val language: String = "",
    val url: String = "",
) {
    /** 편집용 id 를 제외한 내용 비교. */
    fun sameContent(other: Block) = copy(id = other.id) == other
}

fun newId(): String = Uuid.random().toString()
