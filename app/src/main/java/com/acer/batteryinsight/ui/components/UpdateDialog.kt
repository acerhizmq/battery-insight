package com.acer.batteryinsight.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acer.batteryinsight.R
import com.acer.batteryinsight.updater.AppUpdateInfo
import java.io.File

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val progress: Int, val downloadedBytes: Long, val totalBytes: Long) : UpdateDownloadState
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState
    data class Error(val message: String) : UpdateDownloadState
}

@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo,
    downloadState: UpdateDownloadState,
    onDismiss: () -> Unit,
    onStartDownload: () -> Unit,
    onInstall: (File) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp
    val fontScale = density.fontScale
    val isCompact = screenWidth < 360.dp || fontScale > 1.15f

    val dialogScrollState = rememberScrollState()
    val changelogScrollState = rememberScrollState()
    val changelogMaxHeight = (screenHeight * 0.22f).coerceIn(60.dp, 160.dp)

    val btnFontSize = if (isCompact) 12.sp else 13.sp
    val btnPadding = PaddingValues(horizontal = if (isCompact) 10.dp else 16.dp, vertical = 8.dp)

    AlertDialog(
        onDismissRequest = {
            if (downloadState !is UpdateDownloadState.Downloading) {
                onDismiss()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = (screenHeight * 0.88f)),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isCompact) 36.dp else 42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (downloadState is UpdateDownloadState.ReadyToInstall)
                            Icons.Rounded.CheckCircle
                        else
                            Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (isCompact) 20.dp else 24.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.battery_insight_update_dialog_title, updateInfo.latestVersion),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = if (fontScale > 1.15f) 16.sp else 18.sp,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = updateInfo.releaseTitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = if (fontScale > 1.15f) 11.sp else 12.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(dialogScrollState)
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.battery_insight_update_changelog),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (fontScale > 1.15f) 12.sp else 13.sp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )

                // Scrollable changelog box with dynamic max height
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp, max = changelogMaxHeight),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                            .verticalScroll(changelogScrollState),
                    ) {
                        Text(
                            text = updateInfo.changelog.ifBlank { "Minor bug fixes and performance improvements." },
                            style = MaterialTheme.typography.bodySmall.copy(
                                lineHeight = if (fontScale > 1.15f) 16.sp else 18.sp,
                                fontSize = if (fontScale > 1.15f) 11.sp else 12.sp,
                                fontFamily = FontFamily.Default,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // Download Progress or State info
                when (downloadState) {
                    is UpdateDownloadState.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            LinearProgressIndicator(
                                progress = { downloadState.progress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(R.string.battery_insight_update_downloading, downloadState.progress),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = if (fontScale > 1.15f) 11.sp else 12.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (downloadState.totalBytes > 0) {
                                    val downloadedMb = downloadState.downloadedBytes / (1024f * 1024f)
                                    val totalMb = downloadState.totalBytes / (1024f * 1024f)
                                    Text(
                                        text = String.format("%.1f / %.1f MB", downloadedMb, totalMb),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = if (fontScale > 1.15f) 11.sp else 12.sp,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    is UpdateDownloadState.Error -> {
                        Text(
                            text = downloadState.message,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = if (fontScale > 1.15f) 11.sp else 12.sp,
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    is UpdateDownloadState.ReadyToInstall -> {
                        Text(
                            text = "Download complete. Tap below to install.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = if (fontScale > 1.15f) 11.sp else 12.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    UpdateDownloadState.Idle -> {
                        if (updateInfo.apkSize > 0) {
                            val sizeMb = updateInfo.apkSize / (1024f * 1024f)
                            Text(
                                text = "Package Size: ${String.format("%.1f MB", sizeMb)}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = if (fontScale > 1.15f) 11.sp else 12.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (downloadState) {
                is UpdateDownloadState.ReadyToInstall -> {
                    Button(
                        onClick = { onInstall(downloadState.apkFile) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = btnPadding,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(if (isCompact) 16.dp else 18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.battery_insight_update_install_btn),
                            fontSize = btnFontSize,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                is UpdateDownloadState.Downloading -> {
                    FilledTonalButton(
                        onClick = {},
                        enabled = false,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = btnPadding,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.battery_insight_checking_updates),
                            fontSize = btnFontSize,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                else -> {
                    Button(
                        onClick = onStartDownload,
                        enabled = updateInfo.downloadUrl != null,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = btnPadding,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(if (isCompact) 16.dp else 18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.battery_insight_update_btn_now),
                            fontSize = btnFontSize,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        dismissButton = {
            if (downloadState !is UpdateDownloadState.Downloading) {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = if (isCompact) 8.dp else 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.battery_insight_update_btn_later),
                        fontSize = btnFontSize,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
    )
}

@Composable
fun UpdateStatusDialog(
    isError: Boolean,
    message: String,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeight = configuration.screenHeightDp.dp
    val fontScale = density.fontScale
    val isCompact = fontScale > 1.15f || configuration.screenWidthDp < 360

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = (screenHeight * 0.70f)),
        icon = {
            Icon(
                imageVector = if (isError) Icons.Rounded.Info else Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (isCompact) 28.dp else 34.dp),
            )
        },
        title = {
            Text(
                text = if (isError) {
                    stringResource(R.string.battery_insight_update_error)
                } else {
                    stringResource(R.string.battery_insight_updates_title)
                },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = if (fontScale > 1.15f) 16.sp else 18.sp,
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = if (fontScale > 1.15f) 13.sp else 14.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(android.R.string.ok),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (fontScale > 1.15f) 13.sp else 14.sp,
                )
            }
        },
        shape = RoundedCornerShape(24.dp),
    )
}

