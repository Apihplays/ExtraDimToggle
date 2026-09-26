package com.extradim.toggle

import android.content.pm.PackageManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShizukuShell.init(applicationContext)
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

    // null = still probing; RootShell.Backend = the working backend.
    var backend by remember { mutableStateOf<RootShell.Backend?>(null) }
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            working = true
            val probed = withContext(Dispatchers.IO) {
                RootShell.probe()
            }
            backend = probed
            enabled = ExtraDimController.isEnabled(context)
            error = when {
                probed != null -> null
                ShizukuShell.isBinderAlive() && !ShizukuShell.isGranted() ->
                    "Root unavailable. Shizuku is running but hasn't been granted access yet."
                else ->
                    "No root and no usable Shizuku server. Grant root to this app, or start Shizuku (wireless debugging) and grant it access."
            }
            working = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // Re-probe automatically once the user answers Shizuku's dialog.
    DisposableEffect(Unit) {
        val listener =
            Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode == ShizukuShell.REQUEST_CODE &&
                    grantResult == PackageManager.PERMISSION_GRANTED
                ) {
                    refresh()
                }
            }
        try {
            Shizuku.addRequestPermissionResultListener(listener)
        } catch (e: Throwable) {
            // Shizuku API unusable on this device; ignore.
        }
        onDispose {
            try {
                Shizuku.removeRequestPermissionResultListener(listener)
            } catch (e: Throwable) {
            }
        }
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
                scope.launch {
                    working = true
                    error = null
                    val success = withContext(Dispatchers.IO) {
                        val current = enabled ?: return@withContext false
                        ExtraDimController.setEnabled(!current)
                    }
                    if (success) {
                        enabled = ExtraDimController.isEnabled(context)
                        ExtraDimWidgetProvider.updateAll(context)
                    } else {
                        error = "Failed to change setting (permission denied?)"
                    }
                    working = false
                }
            },
            enabled = enabled != null && !working && backend != null
        ) {
            Text(if (enabled == true) "Disable" else "Enable")
        }

        Spacer(Modifier.height(16.dp))

        TextButton(
            onClick = { refresh() },
            enabled = !working
        ) { Text("Refresh") }

        // Offer the grant flow only when Shizuku is running but not yet allowed.
        if (backend == null && ShizukuShell.isBinderAlive() && !ShizukuShell.isGranted()) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    error = null
                    RootShell.requestShizukuPermission()
                },
                enabled = !working
            ) { Text("Grant Shizuku access") }
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
