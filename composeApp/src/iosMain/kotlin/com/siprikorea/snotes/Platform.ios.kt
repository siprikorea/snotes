package com.siprikorea.snotes

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

actual val isApplePlatform: Boolean = true
