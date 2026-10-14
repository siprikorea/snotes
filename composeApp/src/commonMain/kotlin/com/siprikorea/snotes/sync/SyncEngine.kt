package com.siprikorea.snotes.sync

import com.siprikorea.snotes.markdown.MarkdownCodec
import com.siprikorea.snotes.model.Page
import com.siprikorea.snotes.store.LocalStore
import com.siprikorea.snotes.store.PageRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

@Serializable
data class ManifestEntry(
    val fileId: String,
    /** 마지막 동기화 때 로컬 Markdown 해시. 현재 해시와 다르면 로컬이 바뀐 것. */
    val syncedHash: String,
    /** 마지막으로 본 Drive md5. 현재 md5 와 다르면 원격이 바뀐 것. */
    val remoteMd5: String?,
)

@Serializable
data class SyncManifest(
    val rootFolderId: String? = null,
    val pages: Map<String, ManifestEntry> = emptyMap(),
)

data class SyncReport(val pulled: Int, val pushed: Int, val deleted: Int, val conflicts: Int)

/**
 * Google Drive 와 양방향 동기화.
 *
 * Drive 구조: `S Notes/` 폴더 아래 페이지마다 `제목.md`, 하위 페이지가 있으면 같은 이름의 폴더가 옆에 생기고
 * 자식 페이지는 그 폴더 안에 놓인다. 파일/폴더는 `appProperties`(snotesId / snotesFolder) 로 식별하므로
 * 제목이 바뀌어도(파일 이름 변경) 같은 페이지로 인식한다. 페이지의 부모·순서 등은 frontmatter 가 기준이다.
 */
class SyncEngine(
    private val repo: PageRepository,
    private val store: LocalStore,
    private val clock: Clock = Clock.System,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun sync(api: DriveApi): SyncReport {
        var manifest = store.read(MANIFEST)?.let { runCatching { json.decodeFromString<SyncManifest>(it) }.getOrNull() } ?: SyncManifest()
        val entries = manifest.pages.toMutableMap()
        var pulled = 0
        var pushed = 0
        var deleted = 0
        var conflicts = 0

        val remote = api.listAll()
        val rootId = remote.firstOrNull { it.isFolder && it.appProperties[ROOT_KEY] == "true" }?.id
            ?: api.createFolder(ROOT_NAME, null, mapOf(ROOT_KEY to "true")).id
        if (manifest.rootFolderId != null && manifest.rootFolderId != rootId) entries.clear() // 다른 계정/새 폴더
        val remotePages = remote.filter { !it.isFolder && it.appProperties[PAGE_KEY] != null }
            .associateBy { it.appProperties.getValue(PAGE_KEY) }
        val remoteFolders = remote.filter { it.isFolder && it.appProperties[FOLDER_KEY] != null }
            .associateBy { it.appProperties.getValue(FOLDER_KEY) }
            .toMutableMap()
        val tombstones = repo.tombstones()

        fun localChanged(id: String): Boolean {
            val text = repo.storedText(id) ?: return false
            return entries[id]?.syncedHash != hash(text)
        }

        // 1) Pull
        for ((id, file) in remotePages) {
            if (id in tombstones) continue
            val entry = entries[id]
            if (entry != null && entry.remoteMd5 == file.md5Checksum && repo[id] != null) continue
            val remotePage = MarkdownCodec.parse(api.download(file.id)).let { it.copy(meta = it.meta.copy(id = id)) }
            val local = repo[id]
            when {
                local == null || !localChanged(id) -> {
                    val text = repo.putFromRemote(remotePage)
                    entries[id] = ManifestEntry(file.id, hash(text), file.md5Checksum)
                    pulled++
                }
                local.meta.updated >= remotePage.meta.updated -> {
                    // 로컬이 최신: 원격 내용은 사본으로 남기고 로컬은 아래 Push 에서 덮어쓴다.
                    repo.saveConflictCopy(remotePage, conflictLabel())
                    entries[id] = (entry ?: ManifestEntry(file.id, "", null)).copy(fileId = file.id, remoteMd5 = file.md5Checksum)
                    conflicts++
                }
                else -> {
                    repo.saveConflictCopy(local, conflictLabel())
                    val text = repo.putFromRemote(remotePage)
                    entries[id] = ManifestEntry(file.id, hash(text), file.md5Checksum)
                    conflicts++
                    pulled++
                }
            }
        }
        // Drive 에서 지워진 페이지: 로컬이 그대로면 지우고, 로컬이 바뀌었으면 다시 올린다.
        for (id in entries.keys.toList()) {
            if (id in remotePages || id in tombstones) continue
            if (repo[id] != null && !localChanged(id)) {
                repo.removeFromRemote(id)
                deleted++
            }
            entries.remove(id)
        }

        // 2) Push
        val pages = repo.pages.value
        val names = fileNames(pages.values)
        val folderOf = HashMap<String, String>() // 페이지 id -> 그 자식들이 들어갈 폴더 id

        suspend fun parentFolder(page: Page, depth: Int = 0): String {
            val parentId = page.meta.parentId ?: return rootId
            val parent = pages[parentId] ?: return rootId
            if (depth > 64) return rootId // 순환 방지
            folderOf[parentId]?.let { return it }
            val container = parentFolder(parent, depth + 1)
            val name = names.getValue(parentId)
            val existing = remoteFolders[parentId]
            val folder = when {
                existing == null -> api.createFolder(name, container, mapOf(FOLDER_KEY to parentId))
                existing.name != name || container !in existing.parents ->
                    api.updateFile(
                        existing.id,
                        name = name.takeIf { it != existing.name },
                        addParent = container.takeIf { it !in existing.parents },
                        removeParent = existing.parents.firstOrNull()?.takeIf { container !in existing.parents },
                    )
                else -> existing
            }
            remoteFolders[parentId] = folder
            folderOf[parentId] = folder.id
            return folder.id
        }

        val ordered = pages.values.sortedBy { depthOf(it, pages) }
        for (page in ordered) {
            val id = page.id
            val text = repo.storedText(id) ?: continue
            val h = hash(text)
            val name = names.getValue(id) + ".md"
            val container = parentFolder(page)
            val entry = entries[id]
            val file = remotePages[id]?.takeIf { id !in tombstones }
            if (entry == null || file == null) {
                val created = api.createFile(name, container, text, mapOf(PAGE_KEY to id))
                entries[id] = ManifestEntry(created.id, h, created.md5Checksum)
                pushed++
                continue
            }
            val contentChanged = h != entry.syncedHash
            val renamed = file.name != name
            val moved = container !in file.parents
            if (contentChanged || renamed || moved) {
                val updated = api.updateFile(
                    file.id,
                    content = text.takeIf { contentChanged },
                    name = name.takeIf { renamed },
                    addParent = container.takeIf { moved },
                    removeParent = file.parents.firstOrNull()?.takeIf { moved },
                )
                entries[id] = ManifestEntry(updated.id, h, updated.md5Checksum ?: entry.remoteMd5)
                if (contentChanged) pushed++
            }
        }

        // 3) 영구 삭제된 페이지를 Drive 휴지통으로
        for (id in tombstones) {
            remotePages[id]?.let { api.trash(it.id); deleted++ }
            remoteFolders[id]?.let { api.trash(it.id) }
            entries.remove(id)
        }
        repo.clearTombstones(tombstones)

        manifest = SyncManifest(rootId, entries)
        store.write(MANIFEST, json.encodeToString(SyncManifest.serializer(), manifest))
        return SyncReport(pulled, pushed, deleted, conflicts)
    }

    private fun conflictLabel(): String = "충돌 " + clock.now().toString().take(10)

    private fun depthOf(page: Page, pages: Map<String, Page>): Int {
        var d = 0
        var cur = page.meta.parentId
        while (cur != null && d < 64) {
            d++
            cur = pages[cur]?.meta?.parentId
        }
        return d
    }

    companion object {
        const val ROOT_NAME = "S Notes"
        const val ROOT_KEY = "snotesRoot"
        const val PAGE_KEY = "snotesId"
        const val FOLDER_KEY = "snotesFolder"
        private const val MANIFEST = "sync/manifest.json"

        /** 형제 사이에서 겹치지 않는, 파일 시스템에 안전한 이름(확장자 제외). */
        fun fileNames(pages: Collection<Page>): Map<String, String> {
            val out = HashMap<String, String>()
            pages.groupBy { it.meta.parentId }.values.forEach { siblings ->
                val used = HashSet<String>()
                siblings.sortedWith(compareBy<Page> { it.meta.order }.thenBy { it.meta.created }.thenBy { it.id }).forEach { p ->
                    val base = sanitize(p.meta.displayTitle)
                    var name = base
                    var k = 2
                    while (!used.add(name.lowercase())) name = "$base (${k++})"
                    out[p.id] = name
                }
            }
            return out
        }

        private val ILLEGAL = Regex("""[\\/:*?"<>|\u0000-\u001f]""")

        fun sanitize(title: String): String =
            title.replace(ILLEGAL, " ").replace(Regex("\\s+"), " ").trim().trim('.').take(100).ifEmpty { "Untitled" }

        /** FNV-1a 64bit. 변경 감지용. */
        fun hash(text: String): String {
            var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
            for (b in text.encodeToByteArray()) {
                h = h xor (b.toLong() and 0xff)
                h *= 0x100000001b3L
            }
            return h.toULong().toString(16)
        }
    }
}
