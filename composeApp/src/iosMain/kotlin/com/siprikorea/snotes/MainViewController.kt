package com.siprikorea.snotes

import androidx.compose.ui.window.ComposeUIViewController
import com.siprikorea.snotes.store.FileLocalStore
import io.ktor.client.HttpClient
import kotlinx.io.files.Path
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController

private val graph: AppGraph by lazy {
    val http = HttpClient()
    AppGraph(store = FileLocalStore(Path(appSupportDir(), "SNotes")), auth = iosGoogleAuth(http), http = http)
}

private fun appSupportDir(): String {
    val urls = NSFileManager.defaultManager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask)
    return (urls.first() as NSURL).path!!
}

@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController { App(graph) }
