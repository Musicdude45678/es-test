package com.reps.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.data.prefs.Settings
import com.reps.app.ui.RepsRoot
import com.reps.app.ui.theme.RepsTheme

class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as RepsApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
            RepsTheme(mode = settings.theme) {
                RepsRoot(container = container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        container.sync.pushIfDirty()
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "reps" || data.host != "connect") return
        val url = data.getQueryParameter("url") ?: return
        val token = data.getQueryParameter("token") ?: return
        val name = data.getQueryParameter("name") ?: ""
        container.pendingConnect.value = ConnectRequest(url, token, name)
        intent.data = null
    }
}
