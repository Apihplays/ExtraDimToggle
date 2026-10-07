package com.extradim.toggle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rootAvailable by remember { mutableStateOf<Boolean?>(null) }
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var toggleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun refresh() {
        scope.launch {
            val (probed, currentEnabled) = withContext(Dispatchers.IO) {
                val ok = RootShell.probe()
                val current = if (ok) ExtraDimController.isEnabled() else null
                ok to current
            }
            rootAvailable = probed
            enabled = currentEnabled
            error = if (probed) {
                null
            } else {
                "Root access unavailable. Grant root access to this app in your root manager."
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refresh()
    }

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
                val current = enabled ?: return@Button
                val target = !current

                // Optimistic UI update: instantly update UI without waiting for IO
                enabled = target
                error = null

                // Cancel previous queued/running toggle so rapid clicks don't stack up
                toggleJob?.cancel()
                toggleJob = scope.launch {
                    // Short debounce (40ms): coalesce burst taps so only the final state runs
                    kotlinx.coroutines.delay(40)
                    val success = withContext(Dispatchers.IO) {
                        ExtraDimController.setEnabled(target)
                    }
                    if (success) {
                        ExtraDimWidgetProvider.updateAll(context)
                    } else {
                        // Revert on failure
                        enabled = current
                        error = "Failed to change setting (permission denied?)"
                    }
                }
            },
            enabled = enabled != null && rootAvailable == true
        ) {
            Text(if (enabled == true) "Disable" else "Enable")
        }

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
