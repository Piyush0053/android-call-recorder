package com.callrecorder.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callrecorder.app.ui.components.CategoryChip
import com.callrecorder.app.ui.components.EmptyState
import com.callrecorder.app.ui.screens.player.PlayerBottomSheet
import com.callrecorder.app.ui.theme.PrimaryBlue

import android.os.Build
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.platform.LocalContext
import com.callrecorder.app.core.utils.PermissionHelper

import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import android.content.Context
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onRequestPermissions: () -> Unit,
    onRequestCompanionRole: () -> Unit = {},
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recordings by viewModel.recordings.collectAsState()
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsState()
    val activePlayingRecord by viewModel.activePlayingRecord.collectAsState()
    val selectedRecordForSheet by viewModel.selectedRecordForSheet.collectAsState()
    val hasAllPermissions by viewModel.hasAllPermissions.collectAsState()
    val hasOverlayPermission by viewModel.hasOverlayPermission.collectAsState()
    val isCompanionRoleHeld by viewModel.isCompanionRoleHeld.collectAsState()
    val prefs = remember { context.getSharedPreferences("call_recorder_settings", Context.MODE_PRIVATE) }
    var isCompanionBannerDismissed by rememberSaveable {
        mutableStateOf(prefs.getBoolean("answer_detection_banner_dismissed", false))
    }
    var showAnswerDetectionInfoDialog by remember { mutableStateOf(false) }

    val isPlaying by viewModel.audioPlayerEngine.isPlaying.collectAsState()
    val currentPositionMs by viewModel.audioPlayerEngine.currentPositionMs.collectAsState()
    val durationMs by viewModel.audioPlayerEngine.durationMs.collectAsState()
    val currentSpeed by viewModel.audioPlayerEngine.currentSpeed.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Call Recorder",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = Color(0xFF2E7D32)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Air-Gapped • No Internet Access",
                                fontSize = 11.sp,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Warning & Background Setup Banners
            if (!hasAllPermissions) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Recording Permissions Required",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFFBF360C)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Microphone & phone state permissions are mandatory to capture calls.",
                                fontSize = 12.sp,
                                color = Color(0xFFE65100),
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Grant", fontSize = 12.sp)
                        }
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !hasOverlayPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF1565C0),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Android 14/15 Background Setup",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF0D47A1)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Enable 'Display over other apps' so calls record while dialer is on screen.",
                                fontSize = 12.sp,
                                color = Color(0xFF1565C0),
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { PermissionHelper.requestOverlayPermission(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Enable", fontSize = 12.sp)
                        }
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isCompanionRoleHeld && !isCompanionBannerDismissed) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneInTalk,
                            contentDescription = null,
                            tint = Color(0xFF512DA8),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Answer Detection Setup (Android 10+)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF311B92)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Smart Acoustic VAD is active. Tap to view Answer Detection details.",
                                fontSize = 12.sp,
                                color = Color(0xFF512DA8),
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { showAnswerDetectionInfoDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF512DA8)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Details", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                isCompanionBannerDismissed = true
                                prefs.edit().putBoolean("answer_detection_banner_dismissed", true).apply()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color(0xFF7E57C2),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Horizontal Category Filter Chips
            val tabs = listOf("All Calls", "Known Contacts", "Unknown Numbers", "Favorites")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                tabs.forEachIndexed { index, label ->
                    CategoryChip(
                        label = label,
                        isSelected = selectedTabIndex == index,
                        onSelected = { viewModel.selectTab(index) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Filtered recordings from Room
            val displayedRecordings = recordings

            // Recording Cards List or Empty State
            if (displayedRecordings.isEmpty()) {
                val emptyDesc = when (selectedTabIndex) {
                    1 -> "No recordings from your saved contacts yet."
                    2 -> "No recordings from unknown numbers yet."
                    3 -> "No favorite calls starred yet."
                    else -> "When an incoming or outgoing call connects, it will be automatically recorded and displayed here."
                }
                EmptyState(
                    title = "No Recordings Found",
                    description = emptyDesc,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(
                        items = displayedRecordings,
                        key = { it.id }
                    ) { record ->
                        val isThisPlaying = isPlaying && activePlayingRecord?.id == record.id
                        RecordingCard(
                            record = record,
                            isCurrentlyPlaying = isThisPlaying,
                            onCardClick = { viewModel.openPlayerSheet(record) },
                            onPlayClick = { viewModel.togglePlayPause(record) },
                            onToggleFavorite = { viewModel.toggleFavorite(record) }
                        )
                    }
                }
            }
        }

        // Modal Waveform Player Sheet
        selectedRecordForSheet?.let { record ->
            ModalBottomSheet(
                onDismissRequest = { viewModel.closePlayerSheet() },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                PlayerBottomSheet(
                    record = record,
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    currentSpeed = currentSpeed,
                    onTogglePlayPause = { viewModel.togglePlayPause(record) },
                    onSeek = { fraction -> viewModel.seekToFraction(fraction) },
                    onSeekForward = { viewModel.seekForward() },
                    onSeekBackward = { viewModel.seekBackward() },
                    onSpeedChange = { speed -> viewModel.setSpeed(speed) },
                    onToggleFavorite = { viewModel.toggleFavorite(record) },
                    onDelete = { viewModel.deleteRecord(record) }
                )
            }
        }

        // Informative Answer Detection Dialog
        if (showAnswerDetectionInfoDialog) {
            AlertDialog(
                onDismissRequest = { showAnswerDetectionInfoDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.PhoneInTalk,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Smart Answer Detection",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Call Recorder uses Acoustic Voice Activity Detection (VAD). During outgoing calls, it automatically buffers audio and waits until the other party speaks before recording.\n\n" +
                                "This ensures ringing tones and silence are skipped automatically without interrupting Google Phone.\n\n" +
                                "You can also manage system app defaults in System Settings.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showAnswerDetectionInfoDialog = false
                            isCompanionBannerDismissed = true
                            prefs.edit().putBoolean("answer_detection_banner_dismissed", true).apply()
                        }
                    ) {
                        Text("Understood")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showAnswerDetectionInfoDialog = false
                            try {
                                val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                context.startActivity(settingsIntent)
                            } catch (_: Exception) {
                                PermissionHelper.openAppSettings(context)
                            }
                        }
                    ) {
                        Text("System Settings")
                    }
                }
            )
        }
    }
}
