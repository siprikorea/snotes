package com.siprikorea.snotes

import com.siprikorea.snotes.markdown.MarkdownCodec
import com.siprikorea.snotes.model.Block
import com.siprikorea.snotes.store.MemoryLocalStore
import com.siprikorea.snotes.store.PageRepository
import com.siprikorea.snotes.sync.DriveApi
import com.siprikorea.snotes.sync.DriveFile
import com.siprikorea.snotes.sync.SyncEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** 메모리 위의 Drive. 파일 내용과 md5(여기서는 내용 해시) 를 흉내 낸다. */
class FakeDrive : DriveApi {
    val files = LinkedHashMap<String, DriveFile>()
    val contents = HashMap<String, String>()
    private var seq = 0
    var calls = 0

    private fun md5(s: String) = SyncEngine.hash(s)

    override suspend fun listAll() = files.values.toList().also { calls++ }
    override suspend fun download(fileId: String) = contents.getValue(fileId).also { calls++ }

    override suspend fun createFolder(name: String, parentId: String?, appProperties: Map<String, String>): DriveFile {
        calls++
        val f = DriveFile("f${seq++}", name, DriveFile.FOLDER_MIME, listOfNotNull(parentId), null, appProperties)
        files[f.id] = f
        return f
    }

    override suspend fun createFile(name: String, parentId: String, content: String, appProperties: Map<String, String>): DriveFile {
        calls++
        val f = DriveFile("d${seq++}", name, "text/markdown", listOf(parentId), md5(content), appProperties)
        files[f.id] = f
        contents[f.id] = content
        return f
    }

    override suspend fun updateFile(fileId: String, content: String?, name: String?, addParent: String?, removeParent: String?): DriveFile {
        calls++
        var f = files.getValue(fileId)
        if (content != null) { contents[fileId] = content; f = f.copy(md5Checksum = md5(content)) }
        if (name != null) f = f.copy(name = name)
        if (addParent != null || removeParent != null) f = f.copy(parents = f.parents - setOfNotNull(removeParent) + listOfNotNull(addParent))
        files[fileId] = f
        return f
    }

    override suspend fun trash(fileId: String) { calls++; files.remove(fileId); contents.remove(fileId) }

    fun byName(name: String) = files.values.single { it.name == name }
    fun pageFile(id: String) = files.values.singleOrNull { it.appProperties[SyncEngine.PAGE_KEY] == id }

    /** 사용자가 Drive 에서 직접 파일을 고친 것처럼. */
    fun editRemote(id: String, transform: (String) -> String) {
        val f = pageFile(id)!!
        val c = transform(contents.getValue(f.id))
        contents[f.id] = c
        files[f.id] = f.copy(md5Checksum = md5(c))
    }
}

/** 시간을 직접 움직일 수 있는 시계(충돌 판정용). */
class TestClock(var now: Instant = Instant.parse("2026-10-14T00:00:00Z")) : Clock {
    override fun now() = now.also { now += 1.seconds }
}

class Device(val clock: TestClock = TestClock()) {
    val store = MemoryLocalStore()
    val repo = PageRepository(store, clock)
    val engine = SyncEngine(repo, store, clock)
    suspend fun sync(drive: FakeDrive) = engine.sync(drive)
}

class SyncEngineTest {

    @Test
    fun firstSyncCreatesFolderTree() = runTest {
        val drive = FakeDrive()
        val a = Device()
        val parent = a.repo.create(null, "Projects")
        val child = a.repo.create(parent.id, "S/Notes: plan")
        a.repo.create(null, "Projects") // 같은 이름 형제

        val report = a.sync(drive)

        assertEquals(3, report.pushed)
        val root = drive.byName("S Notes")
        val projectsFolder = drive.files.values.single { it.isFolder && it.appProperties[SyncEngine.FOLDER_KEY] == parent.id }
        assertEquals("Projects", projectsFolder.name)
        assertEquals(listOf(root.id), projectsFolder.parents)
        assertEquals("S Notes plan.md", drive.pageFile(child.id)!!.name)
        assertEquals(listOf(projectsFolder.id), drive.pageFile(child.id)!!.parents)
        assertTrue(drive.files.values.any { it.name == "Projects (2).md" })
        // 저장된 내용이 그대로 Markdown
        assertEquals(a.repo.storedText(child.id), drive.contents[drive.pageFile(child.id)!!.id])
    }

    @Test
    fun secondSyncWithoutChangesDoesNothing() = runTest {
        val drive = FakeDrive()
        val a = Device()
        a.repo.create(null, "One")
        a.sync(drive)
        drive.calls = 0
        val report = a.sync(drive)
        assertEquals(0, report.pushed + report.pulled + report.deleted + report.conflicts)
        assertEquals(1, drive.calls, "목록 조회만 해야 한다")
    }

    @Test
    fun editsPropagateBetweenDevices() = runTest {
        val drive = FakeDrive()
        val a = Device()
        val b = Device()
        val page = a.repo.create(null, "Shared")
        a.repo.updateBlocks(page.id, listOf(Block(text = "hello")))
        a.sync(drive)

        b.sync(drive)
        assertEquals("hello", b.repo[page.id]!!.blocks.single().text)

        b.repo.updateBlocks(page.id, listOf(Block(text = "edited on b")))
        b.repo.setTitle(page.id, "Renamed")
        b.sync(drive)
        assertEquals("Renamed.md", drive.pageFile(page.id)!!.name)

        a.sync(drive)
        assertEquals("edited on b", a.repo[page.id]!!.blocks.single().text)
        assertEquals("Renamed", a.repo[page.id]!!.meta.title)
    }

    @Test
    fun movingPageMovesFile() = runTest {
        val drive = FakeDrive()
        val a = Device()
        val p1 = a.repo.create(null, "P1")
        val p2 = a.repo.create(null, "P2")
        val c = a.repo.create(p1.id, "C")
        a.sync(drive)
        a.repo.move(c.id, p2.id, 0)
        a.sync(drive)
        val p2Folder = drive.files.values.single { it.appProperties[SyncEngine.FOLDER_KEY] == p2.id }
        assertEquals(listOf(p2Folder.id), drive.pageFile(c.id)!!.parents)
    }

    @Test
    fun conflictKeepsBothVersions() = runTest {
        val drive = FakeDrive()
        val clock = TestClock()
        val a = Device(clock)
        val b = Device(clock)
        val page = a.repo.create(null, "Doc")
        a.sync(drive)
        b.sync(drive)

        a.repo.updateBlocks(page.id, listOf(Block(text = "from a")))
        b.repo.updateBlocks(page.id, listOf(Block(text = "from b"))) // 더 나중
        a.sync(drive)
        val report = b.sync(drive)

        assertEquals(1, report.conflicts)
        assertEquals("from b", b.repo[page.id]!!.blocks.single().text, "최신 쪽이 원래 페이지에 남는다")
        val copy = b.repo.pages.value.values.single { it.id != page.id }
        assertEquals("from a", copy.blocks.single().text)
        assertTrue(copy.meta.title.startsWith("Doc (충돌"))

        a.sync(drive)
        assertEquals("from b", a.repo[page.id]!!.blocks.single().text)
        assertEquals(2, a.repo.pages.value.size)
    }

    @Test
    fun deleteForeverTrashesRemoteAndPropagates() = runTest {
        val drive = FakeDrive()
        val a = Device()
        val b = Device()
        val parent = a.repo.create(null, "Parent")
        val child = a.repo.create(parent.id, "Child")
        a.sync(drive)
        b.sync(drive)

        a.repo.trash(parent.id)
        a.sync(drive)
        b.sync(drive)
        assertTrue(b.repo.isInTrash(child.id), "휴지통 상태도 동기화")

        a.repo.deleteForever(parent.id)
        a.sync(drive)
        assertNull(drive.pageFile(parent.id))
        assertNull(drive.pageFile(child.id))
        assertTrue(drive.files.values.none { it.appProperties[SyncEngine.FOLDER_KEY] == parent.id })

        b.sync(drive)
        assertNull(b.repo[parent.id])
        assertNull(b.repo[child.id])
    }

    @Test
    fun remoteHandEditIsPulled() = runTest {
        val drive = FakeDrive()
        val a = Device()
        val page = a.repo.create(null, "Hand")
        a.sync(drive)
        drive.editRemote(page.id) { it + "\n- added in Drive\n" }
        val report = a.sync(drive)
        assertEquals(1, report.pulled)
        assertEquals("added in Drive", a.repo[page.id]!!.blocks.last().text)
        // 정규화된 로컬 텍스트 때문에 다시 올리지 않는다.
        assertEquals(0, a.sync(drive).pushed)
    }

    @Test
    fun manifestSurvivesRestart() = runTest {
        val drive = FakeDrive()
        val a = Device()
        a.repo.create(null, "Persist")
        a.sync(drive)
        val restarted = PageRepository(a.store, a.clock).also { it.load() }
        val report = SyncEngine(restarted, a.store, a.clock).sync(drive)
        assertEquals(0, report.pushed + report.pulled)
        assertNotNull(restarted.pages.value.values.single())
        assertEquals(MarkdownCodec.parse(a.store.files.values.first { it.startsWith("---") }).meta.title, "Persist")
    }
}
