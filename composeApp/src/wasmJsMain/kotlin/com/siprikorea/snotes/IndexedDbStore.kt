package com.siprikorea.snotes

import com.siprikorea.snotes.store.LocalStore
import kotlinx.coroutines.await
import kotlin.js.Promise

@Suppress("UNUSED_PARAMETER")
private fun idb(op: String, key: String?, value: String?): Promise<JsAny?> = js(
    """
    new Promise((resolve, reject) => {
        const open = indexedDB.open('snotes', 1);
        open.onupgradeneeded = () => open.result.createObjectStore('files');
        open.onerror = () => reject(open.error);
        open.onsuccess = () => {
            const db = open.result;
            const tx = db.transaction('files', (op === 'get' || op === 'keys') ? 'readonly' : 'readwrite');
            const store = tx.objectStore('files');
            let req;
            if (op === 'get') req = store.get(key);
            else if (op === 'put') req = store.put(value, key);
            else if (op === 'del') req = store.delete(key);
            else req = store.getAllKeys();
            req.onsuccess = () => {
                const r = req.result;
                resolve(op === 'keys' ? r.join('\n') : (r === undefined ? null : r));
            };
            req.onerror = () => reject(req.error);
            tx.oncomplete = () => db.close();
        };
    })
    """,
)

/** 웹: 브라우저 IndexedDB 에 Markdown 파일을 저장한다. */
class IndexedDbStore : LocalStore {
    override suspend fun read(name: String): String? = (idb("get", name, null).await<JsAny?>() as? JsString)?.toString()

    override suspend fun write(name: String, text: String) {
        idb("put", name, text).await<JsAny?>()
    }

    override suspend fun delete(name: String) {
        idb("del", name, null).await<JsAny?>()
    }

    override suspend fun list(dir: String): List<String> {
        val all = (idb("keys", null, null).await<JsAny?>() as? JsString)?.toString().orEmpty()
        return all.split('\n').filter { it.startsWith("$dir/") && '/' !in it.removePrefix("$dir/") }.map { it.removePrefix("$dir/") }
    }
}
