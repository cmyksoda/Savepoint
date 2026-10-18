package com.cmyksoda.savepoint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class SavepointActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Savepoint(
                intent.getBooleanExtra("later", false),
                intent.getBooleanExtra("log-play", false),
                intent.getBooleanExtra("settings", false),
            )
        }
    }
}
