package com.extradim.toggle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ExtraDimScreen()
                }
            }
        }
    }
}

@Composable
fun ExtraDimScreen() {
    val scope = rememberCoroutineScope()

    var rootState by remember { mutableStateOf<Boolean?>(null) }
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            working = true
            // All snapshot writes happen on the main thread; only the slow
            // root-shell calls run on Dispatchers.IO.
            val (rootOk, result) = withContext(Dispatchers.IO) {
                if (RootShell.isRootAvailable()) {
                    true to ExtraDimController.isEnabled()
                } else {
                    false to null
                }
            }
            rootState = rootOk
            enabled = result
            if (!rootOk) {
                error = "Root access denied. Grant root to this app in your superuser manager."
            }
            working = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Extra Dim (Reduce Bright Colors)",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        when (enabled) {
            null -> CircularProgressIndicator()
            else -> Text(
                text = if (enabled == true) "Status: ON" else "Status: OFF",
                style = MaterialTheme.typography.titleLarge,
                color = if (enabled == true) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                scope.launch {
                    working = true
                    error = null
                    val success = withContext(Dispatchers.IO) {
                        val current = enabled ?: return@withContext false
                        ExtraDimController.setEnabled(!current)
                    }
                    if (success) {
                        enabled = withContext(Dispatchers.IO) { ExtraDimController.isEnabled() }
                    } else {
                        error = "Failed to change setting (root denied?)"
                    }
                    working = false
                }
            },
            enabled = enabled != null && !working && rootState == true
        ) {
            Text(if (enabled == true) "Disable" else "Enable")
        }

        Spacer(Modifier.height(16.dp))

        TextButton(
            onClick = { refresh() },
            enabled = !working
        ) { Text("Refresh") }

        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
