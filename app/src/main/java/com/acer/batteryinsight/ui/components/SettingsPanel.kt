/*
 * SPDX-FileCopyrightText: 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import com.acer.batteryinsight.utils.IconUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.acer.batteryinsight.R
import com.acer.batteryinsight.ui.BatteryInsightViewModel
import com.acer.batteryinsight.ui.utils.alarmFrequencyLabels
import com.acer.batteryinsight.ui.utils.getRingtoneName

@Composable
fun SettingsPanel(
    viewModel: BatteryInsightViewModel,
    isEnabled: Boolean,
    isNotifEnabled: Boolean,
    onPickSound: () -> Unit,
    onOpenPermissionSetup: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val monitorInterval by viewModel.monitorInterval.collectAsState()
    val resetPlugged by viewModel.resetOnPlugged.collectAsState()
    val resetReboot by viewModel.resetOnReboot.collectAsState()
    val autoResetEnabled by viewModel.autoResetLevelEnabled.collectAsState()
    val autoResetLevel by viewModel.autoResetLevel.collectAsState()
    val batteryAlarmEnabled by viewModel.batteryAlarmEnabled.collectAsState()
    val batteryLowThreshold by viewModel.batteryLowThreshold.collectAsState()
    val batteryHighThreshold by viewModel.batteryHighThreshold.collectAsState()
    val alarmFrequency by viewModel.alarmFrequency.collectAsState()
    val fullChargeAlarmEnabled by viewModel.fullChargeAlarmEnabled.collectAsState()
    val zeroCurrentAlarmEnabled by viewModel.zeroCurrentAlarmEnabled.collectAsState()
    val alarmSound by viewModel.batteryAlarmSound.collectAsState()
    val alarmVibrate by viewModel.batteryAlarmVibrate.collectAsState()

    var showIntervalDialog by remember { mutableStateOf(false) }
    var showResetLevelDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showAlarmFreqDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val freqLabels = remember { alarmFrequencyLabels(context) }
    var isMonetIconEnabled by remember { mutableStateOf(IconUtils.isMonetIconEnabled(context)) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
    ) {
        GroupCard(title = stringResource(R.string.battery_insight_section_general)) {
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.battery_insight_notif_interval),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.battery_insight_notif_interval_summary,
                            monitorInterval / 1000,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickable { showIntervalDialog = true },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    headlineColor = MaterialTheme.colorScheme.onSurface,
                    supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ToggleRow(
                title = stringResource(R.string.battery_insight_monet_icon_title),
                summary = stringResource(R.string.battery_insight_monet_icon_desc),
                checked = isMonetIconEnabled,
                onToggle = { enabled ->
                    isMonetIconEnabled = enabled
                    IconUtils.setMonetIconEnabled(context, enabled)
                },
            )
        }

        Spacer(Modifier.height(14.dp))

        GroupCard(title = stringResource(R.string.battery_insight_section_permission_mode)) {
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.battery_insight_mode_settings_title),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                supportingContent = {
                    Text(
                        stringResource(R.string.battery_insight_mode_settings_summary),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickable { onOpenPermissionSetup() },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    headlineColor = MaterialTheme.colorScheme.onSurface,
                    supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }



        Spacer(Modifier.height(14.dp))

        GroupCard(title = stringResource(R.string.battery_insight_section_notification)) {
            ToggleRow(
                title = stringResource(R.string.battery_insight_notif_toggle),
                summary = stringResource(R.string.battery_insight_notif_toggle_summary),
                checked = isNotifEnabled,
                enabled = isEnabled,
                onToggle = { viewModel.setNotifEnabled(it) },
            )
        }

        Spacer(Modifier.height(14.dp))

        GroupCard(title = stringResource(R.string.battery_insight_section_auto_reset)) {
            ToggleRow(
                title = stringResource(R.string.battery_insight_auto_reset_level_title),
                summary = stringResource(R.string.battery_insight_auto_reset_level_summary),
                checked = autoResetEnabled,
                onToggle = { viewModel.setAutoResetLevelEnabled(it) },
            )
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.battery_insight_auto_reset_level_percent),
                        color = if (autoResetEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        },
                    )
                },
                supportingContent = {
                    Text(
                        stringResource(R.string.battery_insight_percent_value, autoResetLevel),
                        color = if (autoResetEnabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                    )
                },
                modifier = Modifier.clickable(enabled = autoResetEnabled) {
                    showResetLevelDialog = true
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ToggleRow(
                title = stringResource(R.string.battery_insight_reset_plugged),
                summary = stringResource(R.string.battery_insight_reset_plugged_summary),
                checked = resetPlugged,
                onToggle = { viewModel.setResetOnPlugged(it) },
            )
            ToggleRow(
                title = stringResource(R.string.battery_insight_reset_reboot),
                summary = stringResource(R.string.battery_insight_reset_reboot_summary),
                checked = resetReboot,
                onToggle = { viewModel.setResetOnReboot(it) },
            )
        }

        Spacer(Modifier.height(14.dp))

        GroupCard(title = stringResource(R.string.battery_insight_section_alarms)) {
            ToggleRow(
                title = stringResource(R.string.battery_insight_battery_alarm_title),
                summary = stringResource(R.string.battery_insight_battery_alarm_summary),
                checked = batteryAlarmEnabled,
                onToggle = { viewModel.setBatteryAlarmEnabled(it) },
            )

            if (batteryAlarmEnabled) {
                AlarmThresholdSliders(
                    lowThreshold = batteryLowThreshold,
                    highThreshold = batteryHighThreshold,
                    onLowChange = { viewModel.setBatteryLowThreshold(it) },
                    onHighChange = { viewModel.setBatteryHighThreshold(it) },
                )

                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.battery_insight_alarm_frequency),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    supportingContent = {
                        Text(
                            freqLabels.getOrElse(alarmFrequency) {
                                context.getString(R.string.battery_insight_alarm_freq_unknown)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.clickable { showAlarmFreqDialog = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )

                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.battery_insight_alarm_sound),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    supportingContent = {
                        Text(
                            getRingtoneName(context, alarmSound),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.clickable { onPickSound() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )

                ToggleRow(
                    title = stringResource(R.string.battery_insight_alarm_vibrate),
                    summary = stringResource(R.string.battery_insight_alarm_vibrate_summary),
                    checked = alarmVibrate,
                    onToggle = { viewModel.setBatteryAlarmVibrate(it) },
                )
            }

            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )

            ToggleRow(
                title = stringResource(R.string.battery_insight_full_charge_alarm),
                summary = stringResource(R.string.battery_insight_full_charge_alarm_summary),
                checked = fullChargeAlarmEnabled,
                onToggle = { viewModel.setFullChargeAlarmEnabled(it) },
            )

            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )

            ToggleRow(
                title = stringResource(R.string.battery_insight_zero_current_alarm),
                summary = stringResource(R.string.battery_insight_zero_current_alarm_summary),
                checked = zeroCurrentAlarmEnabled,
                onToggle = { viewModel.setZeroCurrentAlarmEnabled(it) },
            )
        }

        Spacer(Modifier.height(14.dp))

        GroupCard {
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.battery_insight_manual_reset),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                supportingContent = {
                    Text(
                        stringResource(R.string.battery_insight_manual_reset_summary),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickable { showResetConfirmDialog = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        Spacer(Modifier.height(14.dp))

        GroupCard(title = stringResource(R.string.battery_insight_about_title)) {
            ListItem(
                leadingContent = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.size(46.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_battery_insight),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                },
                headlineContent = {
                    Text(
                        stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.battery_insight_version_format,
                            com.acer.batteryinsight.BuildConfig.VERSION_NAME,
                            com.acer.batteryinsight.BuildConfig.VERSION_CODE,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        Spacer(Modifier.height(14.dp))

        GroupCard(title = stringResource(R.string.battery_insight_updates_title)) {
            val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
            ListItem(
                leadingContent = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                },
                headlineContent = {
                    Text(
                        stringResource(R.string.battery_insight_check_updates),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        "v${com.acer.batteryinsight.BuildConfig.VERSION_NAME}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                modifier = Modifier.clickable(enabled = !isCheckingUpdate) {
                    viewModel.checkForUpdates(isManual = true)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        Spacer(Modifier.height(14.dp))

        GroupCard(title = stringResource(R.string.battery_insight_contact_title)) {
            ListItem(
                leadingContent = {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF24A1DE).copy(alpha = 0.15f),
                        modifier = Modifier.size(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_telegram),
                                contentDescription = "Telegram",
                                tint = Color(0xFF24A1DE),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                },
                headlineContent = {
                    Text(
                        stringResource(R.string.battery_insight_contact_telegram),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                supportingContent = {
                    Text(
                        stringResource(R.string.battery_insight_contact_telegram_desc),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickable {
                    try {
                        val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=acerhizm"))
                        context.startActivity(telegramIntent)
                    } catch (_: Exception) {
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/acerhizm"))
                        context.startActivity(webIntent)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }

    if (showIntervalDialog) {
        IntervalDialog(
            currentInterval = monitorInterval,
            onDismiss = { showIntervalDialog = false },
            onSelect = {
                viewModel.setMonitorInterval(it)
                showIntervalDialog = false
            },
        )
    }

    if (showResetLevelDialog) {
        ResetThresholdDialog(
            currentLevel = autoResetLevel,
            onDismiss = { showResetLevelDialog = false },
            onConfirm = {
                viewModel.setAutoResetLevel(it)
                showResetLevelDialog = false
            },
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text(stringResource(R.string.battery_insight_reset_dialog_title)) },
            text = { Text(stringResource(R.string.battery_insight_reset_dialog_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetStats()
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.battery_insight_reset_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text(stringResource(R.string.battery_insight_cancel))
                }
            },
        )
    }

    if (showAlarmFreqDialog) {
        AlarmFrequencyDialog(
            current = alarmFrequency,
            labels = freqLabels,
            onDismiss = { showAlarmFreqDialog = false },
            onSelect = {
                viewModel.setAlarmFrequency(it)
                showAlarmFreqDialog = false
            },
        )
    }
}

@Composable
private fun ResetThresholdDialog(
    currentLevel: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(currentLevel.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.battery_insight_auto_reset_level_percent)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 3) text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.battery_insight_percentage_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = {
                val level = text.toIntOrNull()?.coerceIn(1, 100) ?: 100
                onConfirm(level)
            }) {
                Text(stringResource(R.string.battery_insight_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.battery_insight_cancel))
            }
        },
    )
}

@Composable
private fun IntervalDialog(
    currentInterval: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val options = listOf(
        5000 to stringResource(R.string.battery_insight_interval_5s),
        10000 to stringResource(R.string.battery_insight_interval_10s),
        15000 to stringResource(R.string.battery_insight_interval_15s),
        30000 to stringResource(R.string.battery_insight_interval_30s),
        60000 to stringResource(R.string.battery_insight_interval_60s),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.battery_insight_notif_interval)) },
        text = {
            Column {
                options.forEach { (ms, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(ms) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = currentInterval == ms, onClick = { onSelect(ms) })
                        Spacer(Modifier.padding(horizontal = 6.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.battery_insight_cancel))
            }
        },
    )
}

@Composable
private fun AlarmFrequencyDialog(
    current: Int,
    labels: List<String>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.battery_insight_alarm_frequency_title)) },
        text = {
            Column {
                labels.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = current == index, onClick = { onSelect(index) })
                        Spacer(Modifier.padding(horizontal = 6.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.battery_insight_cancel))
            }
        },
    )
}

@Composable
private fun AlarmThresholdSliders(
    lowThreshold: Int,
    highThreshold: Int,
    onLowChange: (Int) -> Unit,
    onHighChange: (Int) -> Unit,
) {
    var localLow by remember { mutableIntStateOf(lowThreshold) }
    var localHigh by remember { mutableIntStateOf(highThreshold) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(lowThreshold, highThreshold) {
        if (!dragging) {
            localLow = lowThreshold.coerceIn(1, 98)
            localHigh = highThreshold.coerceIn(2, 99)
            if (localLow >= localHigh) {
                localLow = (localHigh - 1).coerceAtLeast(1)
            }
        }
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.battery_insight_alarm_low_threshold),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            stringResource(R.string.battery_insight_percent_value, localLow),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = localLow.toFloat(),
            onValueChange = { value ->
                dragging = true
                val next = value.toInt().coerceIn(1, 98)
                localLow = next.coerceAtMost(localHigh - 1)
            },
            onValueChangeFinished = {
                dragging = false
                onLowChange(localLow)
            },
            valueRange = 1f..(localHigh - 1).coerceAtLeast(2).toFloat(),
        )

        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(R.string.battery_insight_alarm_high_threshold),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            stringResource(R.string.battery_insight_percent_value, localHigh),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = localHigh.toFloat(),
            onValueChange = { value ->
                dragging = true
                val next = value.toInt().coerceIn(2, 99)
                localHigh = next.coerceAtLeast(localLow + 1)
            },
            onValueChangeFinished = {
                dragging = false
                onHighChange(localHigh)
            },
            valueRange = (localLow + 1).coerceAtMost(98).toFloat()..99f,
        )
    }
}
