package com.siprikorea.snotes

import com.siprikorea.snotes.model.Block
import com.siprikorea.snotes.store.MemoryLocalStore
import com.siprikorea.snotes.store.PageRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageRepositoryTest {

    @Test
    fun persistsAndReloads() = runTest {
        val store = MemoryLocalStore()
        val repo = PageRepository(store, TestClock())
        val p = repo.create(null, "Hello")
        repo.updateBlocks(p.id, listOf(Block(text = "body")))
        val again = PageRepository(store).also { it.load() }
        assertEquals("Hello", again[p.id]!!.meta.title)
        assertEquals("body", again[p.id]!!.blocks.single().text)
    }

    @Test
    fun moveKeepsOrderAndRejectsCycles() = runTest {
        val repo = PageRepository(MemoryLocalStore(), TestClock())
        val a = repo.create(null, "A")
        val b = repo.create(null, "B")
        val c = repo.create(null, "C")
        repo.move(c.id, null, 0)
        assertEquals(listOf("C", "A", "B"), repo.children(null).map { it.title })
        repo.move(b.id, null, 1)
        assertEquals(listOf("C", "B", "A"), repo.children(null).map { it.title })

        assertTrue(repo.move(b.id, a.id, 0))
        assertFalse(repo.move(a.id, b.id, 0), "자손 아래로 옮길 수 없다")
        assertEquals(listOf("A"), repo.ancestors(b.id).map { it.title })
    }

    @Test
    fun trashRestoreAndDelete() = runTest {
        val repo = PageRepository(MemoryLocalStore(), TestClock())
        val a = repo.create(null, "A")
        val child = repo.create(a.id, "Child")
        repo.trash(a.id)
        assertTrue(repo.isInTrash(child.id))
        assertEquals(listOf(a.id), repo.trashRoots().map { it.id })
        assertTrue(repo.search("child").isEmpty())

        repo.restore(a.id)
        assertFalse(repo.isInTrash(child.id))
        assertEquals("Child", repo.search("chi").single().page.title)

        repo.trash(a.id)
        repo.emptyTrash()
        assertNull(repo[child.id])
        assertEquals(setOf(a.id, child.id), repo.tombstones())
    }

    @Test
    fun searchFindsBlockText() = runTest {
        val repo = PageRepository(MemoryLocalStore(), TestClock())
        val p = repo.create(null, "Recipes")
        repo.updateBlocks(p.id, listOf(Block(text = "Add two cups of flour")))
        val hit = repo.search("FLOUR").single()
        assertTrue(hit.snippet.contains("flour"))
    }
}
