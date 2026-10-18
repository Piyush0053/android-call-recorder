package com.callrecorder.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.PhoneInTalk
import com.callrecorder.app.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onRequestCompanionRole: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isAutoRecordEnabled by viewModel.isAutoRecordEnabled.collectAsState()
    val isRecordUnknownEnabled by viewModel.isRecordUnknownEnabled.collectAsState()
    val gainMultiplier by viewModel.gainMultiplier.collectAsState()
    val totalStorageBytes by viewModel.totalStorageBytes.collectAsState()
    val isBatteryExempt by viewModel.isBatteryExempt.collectAsState()
    val isOverlayGranted by viewModel.isOverlayGranted.collectAsState()
    val isSpeakerphoneBoostEnabled by viewModel.isSpeakerphoneBoostEnabled.collectAsState()
    val isCompanionRoleHeld by viewModel.isCompanionRoleHeld.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.refreshBatteryOptimizationStatus()
        viewModel.refreshOverlayStatus()
        viewModel.refreshCompanionRoleStatus()
    }

    val formattedStorage = if (totalStorageBytes < 1024 * 1024) {
        "%.1f KB".format(totalStorageBytes / 1024.0)
    } else {
        "%.1f MB".format(totalStorageBytes / (1024.0 * 1024.0))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Settings & Privacy") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Security & Air-Gapped Assurance Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE8F5E9)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Air-Gapped & Firewalled",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1B5E20)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Zero internet permissions are declared in this app. Android's Linux kernel and SELinux sandbox strictly forbid network socket creation, ensuring complete offline privacy.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Recording Automation Section
            Text(
                text = "CALL AUTOMATION",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF757575)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Auto Record Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatic Call Recording",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Start recording automatically when calls connect",
                                fontSize = 12.sp,
                                color = Color(0xFF757575)
                            )
                        }
                        Switch(
                            checked = isAutoRecordEnabled,
                            onCheckedChange = { viewModel.setAutoRecordEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Record Unknown Numbers Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Record Unknown Numbers",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Capture conversations with unsaved callers",
                                fontSize = 12.sp,
                                color = Color(0xFF757575)
                            )
                        }
                        Switch(
                            checked = isRecordUnknownEnabled,
                            onCheckedChange = { viewModel.setRecordUnknownEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Audio Tuning Section
            Text(
                text = "AUDIO TUNING & HARDWARE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF757575)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Software Gain Boost: %.1fx".format(gainMultiplier),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                    Text(
                        text = "Amplifies caller voice from earpiece/speaker while balancing both sides.",
                        fontSize = 12.sp,
                        color = Color(0xFF757575),
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )

                    Slider(
                        value = gainMultiplier,
                        onValueChange = { viewModel.setGainMultiplier(it) },
                        valueRange = 1.0f..3.0f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryBlue,
                            activeTrackColor = PrimaryBlue
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Speakerphone Boost",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Automatically engages speakerphone during active calls to maximize acoustic capture of both caller and remote party.",
                                fontSize = 12.sp,
                                color = Color(0xFF757575)
                            )
                        }
                        Switch(
                            checked = isSpeakerphoneBoostEnabled,
                            onCheckedChange = { viewModel.setSpeakerphoneBoostEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // System Reliability & Battery Section
            Text(
                text = "SYSTEM RELIABILITY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF757575)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isBatteryExempt) Icons.Default.CheckCircle else Icons.Default.BatteryAlert,
                        contentDescription = null,
                        tint = if (isBatteryExempt) Color(0xFF2E7D32) else Color(0xFFF57C00),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isBatteryExempt) "Battery Optimization: Protected" else "Battery Optimization: Unrestricted Needed",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isBatteryExempt) "Android OS will not terminate recordings." else "Exempt app so background recording stays active.",
                            fontSize = 12.sp,
                            color = Color(0xFF757575)
                        )
                    }

                    if (!isBatteryExempt) {
                        OutlinedButton(
                            onClick = { viewModel.requestBatteryOptimizationExemption() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Exempt", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Android 14/15 Background Overlay Exemption
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isOverlayGranted) Icons.Default.CheckCircle else Icons.Default.Security,
                        contentDescription = null,
                        tint = if (isOverlayGranted) Color(0xFF2E7D32) else Color(0xFF1976D2),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isOverlayGranted) "Background Recording: Eligible" else "Background Recording: Exemption Needed",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isOverlayGranted) "Exempt from Android 14+ background microphone restrictions." else "Grant 'Display over other apps' to allow background recording while call dialer is visible.",
                            fontSize = 12.sp,
                            color = Color(0xFF757575)
                        )
                    }

                    if (!isOverlayGranted) {
                        OutlinedButton(
                            onClick = { viewModel.requestOverlayPermission() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Enable", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Call Answer Detection: Smart VAD Active",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Acoustic Voice Activity Detection (VAD) automatically skips dial tones and begins recording when the other party answers.",
                                fontSize = 12.sp,
                                color = Color(0xFF757575)
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    val settingsIntent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                    context.startActivity(settingsIntent)
                                } catch (_: Exception) {
                                    onRequestCompanionRole()
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("System Apps", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Storage Details
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color(0xFF5F6368),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Internal Scoped Storage",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "$formattedStorage used by recordings",
                            fontSize = 12.sp,
                            color = Color(0xFF757575)
                        )
                    }
                }
            }
        }
    }
}
