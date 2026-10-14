package com.siprikorea.snotes

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.ktor.client.HttpClient
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val graph = AppGraph(store = IndexedDbStore(), auth = WebGoogleAuth(), http = HttpClient())
    ComposeViewport(document.body!!) { App(graph) }
    document.getElementById("loading")?.remove()
}
