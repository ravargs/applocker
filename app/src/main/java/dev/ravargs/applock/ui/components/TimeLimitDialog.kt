package dev.ravargs.applock.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun TimeLimitDialog(
    currentLimitMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    // Mode selection: 0 means Always Protect, >0 means Time Limit
    var isAlwaysProtect by remember { mutableStateOf(currentLimitMinutes == 0) }
    
    // Parse current minutes into Hours and Minutes
    val initialHours = if (currentLimitMinutes > 0) (currentLimitMinutes / 60).toString() else ""
    val initialMinutes = if (currentLimitMinutes > 0) (currentLimitMinutes % 60).toString() else ""
    
    var hours by remember { mutableStateOf(initialHours) }
    var minutes by remember { mutableStateOf(initialMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = {
            Text(
                text = "Daily Usage Limit",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Choice 1: Always Protect
                Surface(
                    onClick = { isAlwaysProtect = true },
                    shape = MaterialTheme.shapes.medium,
                    color = if (isAlwaysProtect) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = if (!isAlwaysProtect) RowDefaults.border() else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isAlwaysProtect,
                            onClick = null
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Always Protect",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Requires PIN every time app opens",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Choice 2: Custom Time Limit
                Surface(
                    onClick = { isAlwaysProtect = false },
                    shape = MaterialTheme.shapes.medium,
                    color = if (!isAlwaysProtect) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = if (isAlwaysProtect) RowDefaults.border() else null
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !isAlwaysProtect,
                                onClick = null
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Daily Time Limit",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Unlock only after daily limit is reached",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = !isAlwaysProtect,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = hours,
                                    onValueChange = { if (it.length <= 2 && it.all { char -> char.isDigit() }) hours = it },
                                    label = { Text("Hours") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = minutes,
                                    onValueChange = { 
                                        if (it.length <= 2 && it.all { char -> char.isDigit() }) {
                                            val value = it.toIntOrNull() ?: 0
                                            if (value < 60) minutes = it
                                        }
                                    },
                                    label = { Text("Minutes") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    placeholder = { Text("0-59") }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isAlwaysProtect) {
                        onSave(0)
                    } else {
                        val h = hours.toIntOrNull() ?: 0
                        val m = minutes.toIntOrNull() ?: 0
                        val total = (h * 60) + m
                        if (total > 0) {
                            onSave(total)
                        } else {
                            // If user sets 0h 0m in time limit mode, treat as Always Protect or ignore
                            onSave(0)
                        }
                    }
                }
            ) {
                Text("Save Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

object RowDefaults {
    @Composable
    fun border() = androidx.compose.foundation.BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
