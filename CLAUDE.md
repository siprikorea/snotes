# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

S Notes is a Notion-style block-editor notes app built with Kotlin Multiplatform + Compose Multiplatform (Android, iOS, Web/Wasm, Desktop/JVM). Every page is stored as a Markdown file and synced to Google Drive. Code comments, UI strings and README are in Korean, so keep new comments and strings in Korean too.

## Commands

- Desktop run: `./gradlew :composeApp:run` (isolated data dir: `SNOTES_DATA_DIR=/tmp/x ./gradlew :composeApp:run`)
- Android: `./gradlew :androidApp:installDebug`
- Web dev server: `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` (OAuth origin expects `http://localhost:8080`)
- Web dist: `./gradlew :composeApp:wasmJsBrowserDistribution`
- iOS: `cd iosApp && xcodegen generate`, then open `iosApp.xcodeproj` in Xcode (`project.yml` is the source of truth; don't hand-edit the pbxproj)
- Tests (fastest, JVM): `./gradlew :composeApp:desktopTest`; all targets: `./gradlew :composeApp:allTests`
- Single test: `./gradlew :composeApp:desktopTest --tests "com.siprikorea.snotes.SyncEngineTest"` (append `.methodName` for one method)
- Screenshots: `SNOTES_SCREENSHOT_DIR=/some/dir ./gradlew :composeApp:desktopTest --tests "*ScreenshotTest"` renders the UI offscreen to PNGs, which is the way to check layout without launching the app

The Gradle daemon runs on JDK 25 (`gradle/gradle-daemon-jvm.properties`). Configuration cache and build cache are on.

## Architecture

Almost all code lives in `composeApp/src/commonMain`. `androidApp/` and `iosApp/` are thin hosts. Platform source sets only provide the `expect`/`actual` pieces (`Platform.kt`), a `GoogleAuth` implementation, a `LocalStore` and an entry point.

**Wiring:** `AppGraph` is the manual DI root. Each platform entry point builds it with its own `LocalStore` (`FileLocalStore`, or `IndexedDbStore` on web), `GoogleAuth` and Ktor `HttpClient`. It owns `PageRepository` and `SyncController`, and its `CoroutineScope` swallows background exceptions so they don't crash the app.

**Data flow:** editor → repository → local `.md` → Drive
- `model/Page.kt`: `PageMeta` maps 1:1 to the YAML frontmatter. Its `extra` map keeps frontmatter keys the app doesn't know, so they survive a round trip. Blocks are a **flat list** with an `indent` level (like Notion), not a tree: a block's children are the following blocks with a larger indent.
- `markdown/MarkdownCodec.kt`: the only serializer between `Page` and Markdown (GFM, plus `<details>` for toggles, `> [!NOTE]` for callouts, `&nbsp;` for empty paragraphs, 2-space indent per level, `\` escaping, `snotes://page/<id>` links). Any change to it must keep parse(serialize(x)) == x; `MarkdownCodecTest` covers this.
- `store/PageRepository.kt`: keeps all pages in memory as a `StateFlow`, guarded by a `Mutex`, and writes `pages/<id>.md` on every change. The stored Markdown text is the baseline for sync. `localEdits` fires only for user edits, never for pulled changes, and it drives auto-sync.
- `ui/editor/EditorState.kt`: Compose-state mirror of the blocks (`EditorBlock` with `TextFieldValue`). It saves to the repo after a 400ms debounce. `replaceFromRepository` skips the replace while a save is pending, so remote updates don't overwrite in-progress typing.
- `sync/SyncEngine.kt`: two-way sync against the `DriveApi` interface. A manifest (`sync/manifest.json`) stores, per page, the local hash and Drive `md5Checksum` from the last sync; that is how local and remote changes are detected. Drive files and folders are identified by `appProperties` (`snotesId` / `snotesFolder`), not by name, so renaming a page renames the file. On a conflict, the side with the newer `updated` keeps the page and the other side becomes a `제목 (충돌 YYYY-MM-DD)` copy. Trash state syncs, and permanent deletes are tracked as tombstones.
- `sync/SyncController.kt`: tracks sign-in state and `SyncStatus`, and triggers sync on app start, on foreground (after more than 1 minute), 30s after edits, and on manual sync.
- `sync/DriveApi.kt`: `GoogleDriveApi` is the real Ktor implementation. Tests use `FakeDrive` (in `SyncEngineTest`), which can simulate two devices, so sync logic is tested without network access.

**UI:** `App.kt` holds the root layout: a fixed sidebar on wide screens, a drawer on narrow ones. `ui/AppUiState.kt` handles navigation (current page plus back stack) and dialog visibility. Feature screens are `Sidebar`, `PageScreen`, `Dialogs` and `editor/*`. `InlineMarkdown.kt` styles `**bold**`/`*italic*`/etc. live while editing; the raw markers stay in the text.

**Auth:** OAuth client IDs live in `config/OAuthConfig.kt` and are placeholders until configured. Without them the app runs local-only. The only scope is `drive.file`, so the app sees only files it created. Login per platform: Android uses Play Services `AuthorizationClient`; iOS and Desktop use `PkceOAuth` (iOS via `ASWebAuthenticationSession`, Desktop via a loopback redirect); Web uses a GIS token popup (session-only, about 1h).

## Known limitations (by design for now)

There are no database/table views, images are URL-only, the sidebar has no drag reordering (use the "옮기기" menu), and files created directly in Drive are invisible to the app (because of the `drive.file` scope).
