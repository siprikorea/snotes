package com.siprikorea.snotes

import androidx.compose.runtime.Composable

/** 시스템 뒤로가기 처리 (Android 만 해당). */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** 단축키 표기에 쓸 수정 키(macOS/iOS 는 ⌘). */
expect val isApplePlatform: Boolean
