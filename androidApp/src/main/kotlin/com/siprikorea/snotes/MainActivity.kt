package com.siprikorea.snotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val (graph, auth) = AndroidAppGraph.get(this)
        auth.register(this)
        setContent { App(graph) }
    }
}
