package com.siprikorea.snotes

import androidx.compose.runtime.Composable
import kotlinx.browser.window

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

actual val isApplePlatform: Boolean = window.navigator.platform.contains("Mac") || window.navigator.platform.startsWith("i")
