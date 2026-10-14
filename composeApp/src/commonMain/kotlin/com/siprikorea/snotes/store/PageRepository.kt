package com.siprikorea.snotes.store

import com.siprikorea.snotes.markdown.MarkdownCodec
import com.siprikorea.snotes.model.Block
import com.siprikorea.snotes.model.BlockType
import com.siprikorea.snotes.model.Page
import com.siprikorea.snotes.model.PageMeta
import com.siprikorea.snotes.model.newId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock

data class SearchHit(val page: PageMeta, val snippet: String)

/**
 * 모든 페이지를 메모리에 들고 있고, 변경될 때마다 `pages/<id>.md` 로 저장한다.
 * 로컬에 저장된 Markdown 텍스트가 동기화의 기준이다.
 */
class PageRepository(
    private val store: LocalStore,
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()
    private val _pages = MutableStateFlow<Map<String, Page>>(emptyMap())
    val pages: StateFlow<Map<String, Page>> = _pages.asStateFlow()

    private val texts = HashMap<String, String>()
    private val tombstones = LinkedHashSet<String>()

    private val _localEdits = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 사용자가 로컬에서 무언가를 바꿀 때마다 발생(자동 동기화 예약용). 원격에서 가져온 변경은 제외. */
    val localEdits: SharedFlow<Unit> = _localEdits.asSharedFlow()

    suspend fun load() = mutex.withLock {
        val loaded = HashMap<String, Page>()
        for (name in store.list(PAGES_DIR)) {
            if (!name.endsWith(".md")) continue
            val text = store.read("$PAGES_DIR/$name") ?: continue
            val page = MarkdownCodec.parse(text) { name.removeSuffix(".md") }
            loaded[page.id] = page
            texts[page.id] = text
        }
        store.read(TOMBSTONES)?.let { tombstones += json.decodeFromString(tombstoneSerializer, it) }
        _pages.value = loaded
    }

    // region queries

    operator fun get(id: String): Page? = _pages.value[id]

    fun children(parentId: String?, includeTrashed: Boolean = false): List<PageMeta> =
        _pages.value.values.asSequence().map { it.meta }
            .filter { it.parentId == parentId && (includeTrashed || !it.trashed) }
            .sortedWith(compareBy<PageMeta> { it.order }.thenBy { it.created })
            .toList()

    /** 루트에서 [id] 의 부모까지. */
    fun ancestors(id: String): List<PageMeta> {
        val out = ArrayList<PageMeta>()
        var cur = get(id)?.meta?.parentId
        val seen = HashSet<String>()
        while (cur != null && seen.add(cur)) {
            val m = get(cur)?.meta ?: break
            out += m
            cur = m.parentId
        }
        return out.asReversed()
    }

    /** 자기 자신 또는 조상이 휴지통에 있는지. */
    fun isInTrash(id: String): Boolean = get(id)?.meta?.trashed == true || ancestors(id).any { it.trashed }

    /** 휴지통 목록에 보일 페이지(조상은 휴지통에 없는, 직접 버린 페이지). */
    fun trashRoots(): List<PageMeta> = _pages.value.values.map { it.meta }
        .filter { it.trashed && ancestors(it.id).none { a -> a.trashed } }
        .sortedByDescending { it.updated }

    fun favorites(): List<PageMeta> = _pages.value.values.map { it.meta }
        .filter { it.favorite && !isInTrash(it.id) }
        .sortedBy { it.title.lowercase() }

    fun descendants(id: String): List<String> {
        val byParent = _pages.value.values.groupBy { it.meta.parentId }
        val out = ArrayList<String>()
        val stack = ArrayDeque(listOf(id))
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            byParent[cur]?.forEach { if (it.id !in out) { out += it.id; stack += it.id } }
        }
        return out
    }

    fun search(query: String, limit: Int = 50): List<SearchHit> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return _pages.value.values.asSequence()
            .filter { !isInTrash(it.id) }
            .mapNotNull { page ->
                if (page.meta.title.lowercase().contains(q)) return@mapNotNull SearchHit(page.meta, firstText(page)) to 0
                val block = page.blocks.firstOrNull { it.text.lowercase().contains(q) } ?: return@mapNotNull null
                SearchHit(page.meta, snippet(block.text, q)) to 1
            }
            .sortedWith(compareBy<Pair<SearchHit, Int>> { it.second }.thenByDescending { it.first.page.updated })
            .take(limit).map { it.first }.toList()
    }

    private fun firstText(page: Page) = page.blocks.firstOrNull { it.text.isNotBlank() }?.text?.take(80) ?: ""

    private fun snippet(text: String, q: String): String {
        val at = text.lowercase().indexOf(q)
        val from = (at - 30).coerceAtLeast(0)
        return (if (from > 0) "…" else "") + text.substring(from, (at + q.length + 50).coerceAtMost(text.length)).replace('\n', ' ')
    }

    // endregion

    // region edits (사용자 동작)

    suspend fun create(parentId: String?, title: String = ""): Page = mutex.withLock {
        val now = clock.now()
        val order = (children(parentId, includeTrashed = true).maxOfOrNull { it.order } ?: 0.0) + 1
        val page = Page(PageMeta(id = newId(), title = title, parentId = parentId, order = order, created = now, updated = now))
        saveLocked(page)
        page
    }

    suspend fun updateBlocks(id: String, blocks: List<Block>) = edit(id) { page ->
        val same = page.blocks.size == blocks.size && page.blocks.zip(blocks).all { (a, b) -> a.sameContent(b) }
        if (same) null else page.copy(blocks = blocks)
    }

    suspend fun updateMeta(id: String, f: (PageMeta) -> PageMeta) = edit(id) { page ->
        val meta = f(page.meta)
        if (meta == page.meta) null else page.copy(meta = meta)
    }

    suspend fun setTitle(id: String, title: String) = updateMeta(id) { it.copy(title = title) }
    suspend fun setIcon(id: String, icon: String) = updateMeta(id) { it.copy(icon = icon) }
    suspend fun toggleFavorite(id: String) = updateMeta(id) { it.copy(favorite = !it.favorite) }
    suspend fun trash(id: String) = updateMeta(id) { it.copy(trashed = true) }

    suspend fun restore(id: String) {
        val parent = get(id)?.meta?.parentId
        val parentGone = parent != null && (get(parent) == null || isInTrash(parent))
        updateMeta(id) { it.copy(trashed = false, parentId = if (parentGone) null else it.parentId) }
    }

    /**
     * [id] 를 [newParentId] 의 [index] 번째 자식으로 옮긴다. 자기 자신이나 자손 아래로는 옮길 수 없다.
     * 순서는 이웃 사이의 중간값이라 다른 페이지 파일은 건드리지 않는다.
     */
    suspend fun move(id: String, newParentId: String?, index: Int): Boolean {
        if (newParentId == id || (newParentId != null && newParentId in descendants(id))) return false
        val siblings = children(newParentId).filter { it.id != id }
        val i = index.coerceIn(0, siblings.size)
        val before = siblings.getOrNull(i - 1)?.order
        val after = siblings.getOrNull(i)?.order
        val order = when {
            before == null && after == null -> 1.0
            before == null -> after!! - 1
            after == null -> before + 1
            else -> (before + after) / 2
        }
        updateMeta(id) { it.copy(parentId = newParentId, order = order) }
        return true
    }

    suspend fun duplicate(id: String): Page? {
        val src = get(id) ?: return null
        val now = clock.now()
        val copy = Page(
            src.meta.copy(id = newId(), title = src.meta.title + " (사본)", favorite = false, order = src.meta.order + 0.5, created = now, updated = now),
            src.blocks.map { it.copy(id = newId()) },
        )
        mutex.withLock { saveLocked(copy) }
        _localEdits.tryEmit(Unit)
        return copy
    }

    /** 페이지와 모든 하위 페이지를 영구 삭제하고, 다음 동기화 때 Drive 에서도 지우도록 기록한다. */
    suspend fun deleteForever(id: String) {
        mutex.withLock {
            val ids = listOf(id) + descendants(id)
            ids.forEach { removeLocked(it) }
            tombstones += ids
            store.write(TOMBSTONES, json.encodeToString(tombstoneSerializer, tombstones))
        }
        _localEdits.tryEmit(Unit)
    }

    suspend fun emptyTrash() = trashRoots().forEach { deleteForever(it.id) }

    private suspend fun edit(id: String, f: (Page) -> Page?) {
        val changed = mutex.withLock {
            val page = get(id) ?: return
            val next = f(page) ?: return
            saveLocked(next.copy(meta = next.meta.copy(updated = clock.now())))
            true
        }
        if (changed) _localEdits.tryEmit(Unit)
    }

    // endregion

    // region 동기화용

    /** 로컬에 저장된 Markdown 원문. */
    fun storedText(id: String): String? = texts[id]

    fun allIds(): Set<String> = _pages.value.keys

    fun tombstones(): Set<String> = tombstones.toSet()

    suspend fun clearTombstones(ids: Collection<String>) = mutex.withLock {
        tombstones -= ids.toSet()
        store.write(TOMBSTONES, json.encodeToString(tombstoneSerializer, tombstones))
    }

    /** 원격에서 받은 페이지를 저장한다. 정규화된 Markdown 원문을 돌려준다. */
    suspend fun putFromRemote(page: Page): String = mutex.withLock { saveLocked(page) }

    /** 원격에서 삭제된 페이지를 로컬에서도 지운다(묘비 없이). */
    suspend fun removeFromRemote(id: String) = mutex.withLock { removeLocked(id) }

    /** 충돌 시 진 쪽을 별도 페이지로 남긴다. */
    suspend fun saveConflictCopy(page: Page, label: String): Page = mutex.withLock {
        val now = clock.now()
        val copy = Page(
            page.meta.copy(id = newId(), title = "${page.meta.displayTitle} ($label)", order = page.meta.order + 0.5, created = now, updated = now),
            page.blocks,
        )
        saveLocked(copy)
        copy
    }

    // endregion

    private suspend fun saveLocked(page: Page): String {
        val text = MarkdownCodec.serialize(page)
        store.write("$PAGES_DIR/${page.id}.md", text)
        texts[page.id] = text
        _pages.value = _pages.value + (page.id to page)
        return text
    }

    private suspend fun removeLocked(id: String) {
        store.delete("$PAGES_DIR/$id.md")
        texts.remove(id)
        _pages.value = _pages.value - id
    }

    companion object {
        const val PAGES_DIR = "pages"
        private const val TOMBSTONES = "sync/tombstones.json"
        private val json = Json
        private val tombstoneSerializer = SetSerializer(String.serializer())

        /** 새 페이지/빈 페이지 판별용. */
        fun isEmptyPage(page: Page) = page.meta.title.isBlank() &&
            page.blocks.all { it.type == BlockType.Paragraph && it.text.isBlank() }
    }
}
