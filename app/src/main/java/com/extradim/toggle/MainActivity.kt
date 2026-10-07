package com.extradim.toggle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
    val haptic = LocalHapticFeedback.current
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
            text = "Extra Dim",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Reduce display brightness below minimum",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(36.dp))

        when (enabled) {
            null -> {
                CircularProgressIndicator()
            }
            else -> {
                val isChecked = enabled == true
                val cardColor by animateColorAsState(
                    targetValue = if (isChecked) MaterialTheme.colorScheme.primaryContainer
                                  else MaterialTheme.colorScheme.surfaceVariant,
                    label = "cardColor"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer
                                  else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "contentColor"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (isChecked) 1.15f else 1.0f,
                    label = "iconScale"
                )

                ElevatedCard(
                    onClick = {
                        if (rootAvailable != true) return@ElevatedCard
                        val current = isChecked
                        val target = !current

                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        enabled = target
                        error = null

                        toggleJob?.cancel()
                        toggleJob = scope.launch {
                            kotlinx.coroutines.delay(40)
                            val success = withContext(Dispatchers.IO) {
                                ExtraDimController.setEnabled(target)
                            }
                            if (success) {
                                ExtraDimWidgetProvider.updateAll(context)
                            } else {
                                enabled = current
                                error = "Failed to change setting (permission denied?)"
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(130.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = cardColor,
                        contentColor = contentColor
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_brightness_low),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .scale(iconScale),
                                tint = contentColor
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = if (isChecked) "Active" else "Inactive",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isChecked) "Extra Dim is ON" else "Extra Dim is OFF",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = contentColor.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Switch(
                            checked = isChecked,
                            onCheckedChange = null, // handled by card onClick
                            enabled = rootAvailable == true
                        )
                    }
                }
            }
        }

        error?.let {
            Spacer(Modifier.height(24.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}
