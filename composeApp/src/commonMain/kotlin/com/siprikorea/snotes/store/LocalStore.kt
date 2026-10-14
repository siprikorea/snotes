package com.siprikorea.snotes.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

/**
 * 기기 로컬 저장소. 이름은 `pages/<id>.md`, `sync/manifest.json` 처럼 `/` 로 구분한다.
 * 파일 시스템이 있는 플랫폼은 [FileLocalStore], 웹은 IndexedDB 구현을 쓴다.
 */
interface LocalStore {
    suspend fun read(name: String): String?
    suspend fun write(name: String, text: String)
    suspend fun delete(name: String)

    /** [dir] 바로 아래 파일 이름들(디렉터리 접두어 제외). */
    suspend fun list(dir: String): List<String>
}

class FileLocalStore(private val root: Path) : LocalStore {
    private val fs = SystemFileSystem

    private fun path(name: String) = Path(root, *name.split('/').toTypedArray())

    override suspend fun read(name: String): String? = withContext(Dispatchers.Default) {
        val p = path(name)
        if (!fs.exists(p)) null else fs.source(p).buffered().use { it.readString() }
    }

    override suspend fun write(name: String, text: String) = withContext(Dispatchers.Default) {
        val p = path(name)
        p.parent?.let { fs.createDirectories(it) }
        // 쓰는 도중 종료돼도 기존 파일이 깨지지 않도록 임시 파일에 쓴 뒤 교체한다.
        val tmp = Path(p.parent!!, p.name + ".tmp")
        fs.sink(tmp).buffered().use { it.writeString(text) }
        fs.atomicMove(tmp, p)
    }

    override suspend fun delete(name: String) = withContext(Dispatchers.Default) {
        fs.delete(path(name), mustExist = false)
    }

    override suspend fun list(dir: String): List<String> = withContext(Dispatchers.Default) {
        val p = path(dir)
        if (!fs.exists(p)) emptyList() else fs.list(p).map { it.name }.filterNot { it.endsWith(".tmp") }
    }
}

class MemoryLocalStore : LocalStore {
    val files = LinkedHashMap<String, String>()
    override suspend fun read(name: String) = files[name]
    override suspend fun write(name: String, text: String) { files[name] = text }
    override suspend fun delete(name: String) { files.remove(name) }
    override suspend fun list(dir: String) =
        files.keys.filter { it.startsWith("$dir/") && '/' !in it.removePrefix("$dir/") }.map { it.removePrefix("$dir/") }
}
