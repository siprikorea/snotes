package com.siprikorea.snotes.sync

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

@Serializable
data class DriveFile(
    val id: String,
    val name: String = "",
    val mimeType: String = "",
    val parents: List<String> = emptyList(),
    val md5Checksum: String? = null,
    val appProperties: Map<String, String> = emptyMap(),
) {
    val isFolder: Boolean get() = mimeType == FOLDER_MIME

    companion object {
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
    }
}

/** 동기화에 필요한 Google Drive 기능만 추린 인터페이스. 테스트에서는 가짜 구현을 쓴다. */
interface DriveApi {
    /** 앱이 볼 수 있는(drive.file 범위) 휴지통에 없는 모든 파일. */
    suspend fun listAll(): List<DriveFile>
    suspend fun download(fileId: String): String
    suspend fun createFolder(name: String, parentId: String?, appProperties: Map<String, String>): DriveFile
    suspend fun createFile(name: String, parentId: String, content: String, appProperties: Map<String, String>): DriveFile

    /** [content]/[name] 이 null 이면 그대로 둔다. [addParent]/[removeParent] 로 폴더를 옮긴다. */
    suspend fun updateFile(
        fileId: String,
        content: String? = null,
        name: String? = null,
        addParent: String? = null,
        removeParent: String? = null,
    ): DriveFile

    suspend fun trash(fileId: String)
}

class DriveException(val status: Int, message: String) : Exception(message)

/**
 * Drive REST v3 구현.
 * [token] 은 유효한 액세스 토큰을 돌려주고, [onUnauthorized] 는 401 을 받았을 때 캐시된 토큰을 버린다.
 */
class GoogleDriveApi(
    private val http: HttpClient,
    private val token: suspend () -> String,
    private val onUnauthorized: suspend () -> Unit = {},
) : DriveApi {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class FileList(val files: List<DriveFile> = emptyList(), val nextPageToken: String? = null)

    override suspend fun listAll(): List<DriveFile> {
        val out = ArrayList<DriveFile>()
        var pageToken: String? = null
        do {
            val res = call {
                http.get("$API/files") {
                    it()
                    parameter("q", "trashed = false")
                    parameter("spaces", "drive")
                    parameter("pageSize", 1000)
                    parameter("fields", "nextPageToken,files($FIELDS)")
                    pageToken?.let { t -> parameter("pageToken", t) }
                }
            }
            val list = json.decodeFromString<FileList>(res.bodyAsText())
            out += list.files
            pageToken = list.nextPageToken
        } while (pageToken != null)
        return out
    }

    override suspend fun download(fileId: String): String =
        call { http.get("$API/files/$fileId") { it(); parameter("alt", "media") } }.bodyAsText()

    override suspend fun createFolder(name: String, parentId: String?, appProperties: Map<String, String>): DriveFile {
        val meta = metadata(name, parentId, appProperties, DriveFile.FOLDER_MIME)
        return parse(
            call {
                http.post("$API/files") {
                    it()
                    parameter("fields", FIELDS)
                    setBody(TextContent(meta.toString(), ContentType.Application.Json))
                }
            },
        )
    }

    override suspend fun createFile(name: String, parentId: String, content: String, appProperties: Map<String, String>): DriveFile {
        val meta = metadata(name, parentId, appProperties, MARKDOWN_MIME)
        return parse(
            call {
                http.post("$UPLOAD/files") {
                    it()
                    parameter("uploadType", "multipart")
                    parameter("fields", FIELDS)
                    multipart(meta, content)
                }
            },
        )
    }

    override suspend fun updateFile(
        fileId: String,
        content: String?,
        name: String?,
        addParent: String?,
        removeParent: String?,
    ): DriveFile {
        val meta = buildJsonObject { name?.let { put("name", it) } }
        return parse(
            call {
                http.patch(if (content != null) "$UPLOAD/files/$fileId" else "$API/files/$fileId") {
                    it()
                    parameter("fields", FIELDS)
                    addParent?.let { p -> parameter("addParents", p) }
                    removeParent?.let { p -> parameter("removeParents", p) }
                    if (content != null) {
                        parameter("uploadType", "multipart")
                        multipart(meta, content)
                    } else {
                        setBody(TextContent(meta.toString(), ContentType.Application.Json))
                    }
                }
            },
        )
    }

    override suspend fun trash(fileId: String) {
        call {
            http.patch("$API/files/$fileId") {
                it()
                setBody(TextContent("""{"trashed":true}""", ContentType.Application.Json))
            }
        }
    }

    /** 로그인한 계정 이메일. drive.file 범위만으로 조회할 수 있다. */
    suspend fun userEmail(): String? {
        val body = call { http.get("$API/about") { it(); parameter("fields", "user(emailAddress)") } }.bodyAsText()
        val user = json.parseToJsonElement(body) as? JsonObject
        return ((user?.get("user") as? JsonObject)?.get("emailAddress") as? JsonPrimitive)?.content
    }

    private fun metadata(name: String, parentId: String?, props: Map<String, String>, mime: String) = buildJsonObject {
        put("name", name)
        put("mimeType", mime)
        if (parentId != null) putJsonArray("parents") { add(JsonPrimitive(parentId)) }
        if (props.isNotEmpty()) putJsonObject("appProperties") { props.forEach { (k, v) -> put(k, v) } }
    }

    private fun HttpRequestBuilder.multipart(meta: JsonObject, content: String) {
        val boundary = "snotes-boundary-7f3a9c"
        val body = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(meta.toString()).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: ").append(MARKDOWN_MIME).append("; charset=UTF-8\r\n\r\n")
            append(content).append("\r\n")
            append("--").append(boundary).append("--")
        }
        setBody(TextContent(body, ContentType.parse("multipart/related; boundary=$boundary")))
    }

    private suspend fun parse(res: HttpResponse): DriveFile = json.decodeFromString(res.bodyAsText())

    /** 토큰을 붙여 요청하고, 401 이면 토큰을 새로 받아 한 번 더 시도한다. */
    private suspend fun call(request: suspend (auth: HttpRequestBuilder.() -> Unit) -> HttpResponse): HttpResponse {
        var t = token()
        var res = request { bearerAuth(t) }
        if (res.status == HttpStatusCode.Unauthorized) {
            onUnauthorized()
            t = token()
            res = request { bearerAuth(t) }
        }
        if (!res.status.isSuccess()) throw DriveException(res.status.value, "Drive ${res.status.value}: ${res.bodyAsText().take(300)}")
        return res
    }

    companion object {
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val FIELDS = "id,name,mimeType,parents,md5Checksum,appProperties"
        const val MARKDOWN_MIME = "text/markdown"
    }
}
