# S Notes

Notion 스타일 블록 편집기를 갖춘 노트 앱입니다. 모든 페이지는 **Markdown(.md) 파일**로 저장되고 **Google Drive** 와 동기화됩니다.
Kotlin Multiplatform + Compose Multiplatform 하나의 코드로 **Android · iOS · Web(Wasm) · Desktop(JVM)** 에서 동작합니다.

## 기능

- 중첩 페이지 트리(사이드바), 페이지 아이콘, 경로(브레드크럼), 즐겨찾기, 휴지통(복원/영구 삭제), 검색(⌘/Ctrl+K)
- 블록: 텍스트, 제목 1~3, 글머리/번호/할 일 목록, 토글, 인용, 콜아웃, 코드, 구분선, 이미지, 페이지 링크
- `/` 슬래시 메뉴, Markdown 단축 입력(`# `, `- `, `1. `, `[] `, `> ` 토글, `" ` 인용, ```` ``` ````, `---`)
- 인라인 서식 `**굵게**`, `*기울임*`, `~~취소선~~`, `` `코드` ``, `[링크](url)` — 편집 중에도 스타일 표시
- Tab / Shift+Tab 들여쓰기, 손잡이 드래그로 블록 이동, 블록 메뉴(전환·복제·이동·삭제), 편집 도구 모음
- 페이지 복제·옮기기, "Markdown 보기"로 저장 원문 확인
- 넓은 화면은 고정 사이드바, 좁은 화면은 서랍 메뉴. 라이트/다크 테마

## 저장 형식

로컬에서는 페이지마다 `pages/<id>.md` 하나, Drive 에서는 이렇게 저장됩니다.

```
My Drive/
└── S Notes/
    ├── 프로젝트.md
    ├── 프로젝트/            ← 하위 페이지가 있으면 같은 이름의 폴더
    │   └── 회의록.md
    └── 장보기.md
```

```markdown
---
id: 3f2c9a1e-...
title: "회의록"
icon: "📝"
parent: 8b1d...
order: 1
favorite: false
created: 2026-10-14T12:00:00Z
updated: 2026-10-14T12:30:00Z
---

# 안건

- [ ] 일정 확정

<details>
<summary>토글</summary>

  숨겨진 내용

</details>

> [!NOTE]
> 콜아웃

[📄 하위 페이지](snotes://page/<id>)
```

- GFM 으로 표현할 수 있는 블록은 GFM 그대로, 토글은 `<details>`, 콜아웃은 GitHub 알림 문법, 빈 문단은 `&nbsp;` 로 저장합니다.
- 들여쓰기 한 단계 = 공백 2칸. 블록 문법으로 오해될 텍스트는 `\` 로 이스케이프합니다.
- 다른 편집기로 고친 파일도 읽을 수 있고, 모르는 frontmatter 키는 보존합니다.

## 동기화 방식

`composeApp/.../sync/SyncEngine.kt`

- Drive 파일은 `appProperties.snotesId`(폴더는 `snotesFolder`)로 식별하므로 제목을 바꿔도(=파일 이름 변경) 같은 페이지입니다. 부모·순서는 frontmatter 가 기준이며, 바뀌면 파일을 옮기거나 이름을 바꿉니다.
- 로컬 변경은 마지막 동기화 때의 내용 해시, 원격 변경은 Drive `md5Checksum` 으로 감지합니다.
- 양쪽이 모두 바뀌면 `updated` 가 최신인 쪽이 원래 페이지에 남고, 다른 쪽은 `제목 (충돌 YYYY-MM-DD)` 사본 페이지로 보관합니다.
- 휴지통 상태도 동기화되며, 영구 삭제하면 Drive 의 파일·폴더도 Drive 휴지통으로 이동합니다.
- 앱 시작 시, 앱이 다시 앞으로 올 때(1분 경과 시), 편집 후 30초, 설정의 "지금 동기화"에서 실행됩니다.
- 권한은 `drive.file` 하나뿐이라 **앱이 만든 파일에만** 접근합니다. Drive 에서 직접 수정한 내용은 반영되지만, Drive 에서 새로 만든 .md 파일은 앱이 볼 수 없습니다.

## 빌드와 실행

필요: JDK 17 이상(Gradle 데몬은 `gradle/gradle-daemon-jvm.properties` 의 JDK 25), Android SDK, Xcode, xcodegen(iOS 프로젝트 재생성 시).

| 플랫폼 | 명령 |
|---|---|
| Desktop | `./gradlew :composeApp:run` (데이터 위치 변경: `SNOTES_DATA_DIR=/tmp/x ./gradlew :composeApp:run`) |
| Desktop 설치 파일 | `./gradlew :composeApp:packageDistributionForCurrentOS` |
| Android | `./gradlew :androidApp:installDebug` |
| Web 개발 서버 | `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` |
| Web 배포 파일 | `./gradlew :composeApp:wasmJsBrowserDistribution` → `composeApp/build/dist/wasmJs/productionExecutable/` |
| iOS | `cd iosApp && xcodegen generate` 후 `iosApp.xcodeproj` 를 Xcode 로 열어 실행 (Signing 팀 지정) |
| 테스트 | `./gradlew :composeApp:desktopTest` (전체: `:composeApp:allTests`) |

로컬 저장 위치: macOS `~/Library/Application Support/SNotes`, Windows `%APPDATA%\SNotes`, Linux `~/.local/share/snotes`, Android 앱 내부 저장소, iOS Application Support, Web IndexedDB.

## Google 동기화 설정

OAuth 클라이언트 값은 저장소에 넣지 않고 루트의 `local.properties`(git 제외)에서 읽습니다. 아래를 마치기 전에는 설정 화면에 "OAuth 클라이언트가 설정되지 않았습니다" 가 표시되고 기기 안에만 저장됩니다.

1. [Google Cloud Console](https://console.cloud.google.com/) 에서 프로젝트를 만들고 **Google Drive API** 를 사용 설정합니다.
2. **OAuth 동의 화면**: 범위에 `https://www.googleapis.com/auth/drive.file` 을 추가하고, 테스트 중이면 테스트 사용자에 본인 계정을 추가합니다.
3. **사용자 인증 정보 → OAuth 클라이언트 ID** 를 플랫폼별로 만듭니다.

| 유형 | 입력 | 넣을 곳 |
|---|---|---|
| Android | 패키지 `com.siprikorea.snotes`, 서명 인증서 SHA-1 (`./gradlew :androidApp:signingReport`) | 코드 수정 없음 (Play 서비스가 자동 인식) |
| iOS | 번들 ID `com.siprikorea.snotes` | `snotes.oauth.iosClientId` |
| 데스크톱 앱 | — | `snotes.oauth.desktopClientId`, `snotes.oauth.desktopClientSecret` |
| 웹 애플리케이션 | 승인된 JavaScript 원본: 개발 `http://localhost:8080`, 배포 주소 | `snotes.oauth.webClientId` |

```properties
# local.properties
snotes.oauth.desktopClientId=xxxx.apps.googleusercontent.com
snotes.oauth.desktopClientSecret=GOCSPX-xxxx
snotes.oauth.iosClientId=xxxx.apps.googleusercontent.com
snotes.oauth.webClientId=xxxx.apps.googleusercontent.com
```

빌드할 때 `:composeApp:generateOAuthSecrets` 가 이 값으로 `OAuthSecrets` 를 생성하고, `config/OAuthConfig.kt` 가 그것을 참조합니다. 비어 있는 플랫폼은 로그인 없이 동작합니다. 웹 배포 파일에는 웹 클라이언트 ID 가 그대로 들어가지만, 클라이언트 ID 는 공개돼도 괜찮은 값입니다.

플랫폼별 로그인 방식:
- **Android**: Google Play 서비스 `AuthorizationClient` (계정 선택/동의 화면)
- **iOS**: `ASWebAuthenticationSession` + PKCE, refresh token 은 Keychain
- **Desktop**: 시스템 브라우저 + `127.0.0.1` 루프백 리디렉션 + PKCE, refresh token 은 OS Preferences
- **Web**: Google Identity Services 토큰 팝업. 토큰은 1시간 유효하고 탭 세션에만 저장되므로 만료되면 설정에서 다시 로그인합니다.

## 구조

```
composeApp/src/
  commonMain/  model · markdown(MarkdownCodec) · store(PageRepository, LocalStore)
               sync(SyncEngine, GoogleDriveApi, SyncController) · auth(PkceOAuth, Sha256)
               ui(App, Sidebar, PageScreen, Dialogs, editor/*) · config(OAuthConfig)
  androidMain/ AndroidGoogleAuth, AndroidAppGraph
  iosMain/     IosAuth(ASWebAuthenticationSession), MainViewController
  desktopMain/ DesktopAuth(루프백), main
  wasmJsMain/  IndexedDbStore, WebGoogleAuth(GIS), main, index.html
  commonTest/  Markdown 왕복, 저장소, 두 기기 동기화(가짜 Drive), 편집기 동작, SHA-256/PKCE
  desktopTest/ 오프스크린 렌더링 스크린샷 (SNOTES_SCREENSHOT_DIR 지정 시 PNG 저장)
androidApp/    Activity
iosApp/        SwiftUI 호스트 (project.yml → xcodegen)
```

## 알려진 제한

- 데이터베이스(표/보드/캘린더 뷰)는 아직 없습니다(1차 범위 밖).
- 이미지는 URL 로만 넣을 수 있습니다(파일 업로드 없음).
- Drive 에서 새로 만든 .md 파일은 `drive.file` 권한 특성상 앱에 보이지 않습니다.
- 사이드바 페이지 순서 변경은 "옮기기" 메뉴로 합니다(트리 드래그 미지원).
- 웹에서는 고정폭 폰트가 없어 코드 블록이 Pretendard 로 표시됩니다.
- 실제 Google 로그인/Drive 왕복은 클라이언트 ID 를 넣은 뒤에만 확인할 수 있습니다. 동기화 로직 자체는 가짜 Drive 로 테스트되어 있습니다.
