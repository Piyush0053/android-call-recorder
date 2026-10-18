package com.callrecorder.app.ui.screens.player

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callrecorder.app.core.audio.WaveformExtractor
import com.callrecorder.app.domain.model.CallRecord
import com.callrecorder.app.domain.model.CallType
import com.callrecorder.app.ui.components.ContactAvatar
import com.callrecorder.app.ui.theme.IncomingGreen
import com.callrecorder.app.ui.theme.OutgoingBlue
import com.callrecorder.app.ui.theme.PrimaryBlue
import com.callrecorder.app.ui.theme.StarAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlayerBottomSheet(
    record: CallRecord,
    isPlaying: Boolean,
    currentPositionMs: Int,
    durationMs: Int,
    currentSpeed: Float,
    onTogglePlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalMs = if (durationMs > 0) durationMs else (record.durationSeconds * 1000).toInt()
    val progressFraction = if (totalMs > 0) (currentPositionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f

    val amplitudes = remember(record.id) {
        WaveformExtractor.generateMockWaveform(40)
    }

    val dateFormat = remember { SimpleDateFormat("EEE, MMM d, yyyy • hh:mm a", Locale.getDefault()) }
    val formattedDate = remember(record.timestamp) { dateFormat.format(Date(record.timestamp)) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFE0E0E0))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Contact info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isKnown = record.isKnownContact && !record.contactName.isNullOrBlank()
                val headerTitle = if (isKnown) {
                    record.contactName!!
                } else {
                    record.phoneNumber.takeIf { it.isNotBlank() && !it.equals("Unknown Number", ignoreCase = true) } ?: "Private Number"
                }

                ContactAvatar(
                    name = headerTitle,
                    isKnown = isKnown,
                    size = 52.dp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isKnown) {
                        Text(
                            text = record.phoneNumber,
                            fontSize = 12.sp,
                            color = Color(0xFF757575)
                        )
                    } else if (!headerTitle.equals("Private Number", ignoreCase = true)) {
                        Text(
                            text = "Unsaved Number",
                            fontSize = 11.sp,
                            color = Color(0xFF9E9E9E)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (record.callType == CallType.INCOMING) "Incoming" else "Outgoing",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (record.callType == CallType.INCOMING) IncomingGreen else OutgoingBlue
                        )
                        Text(
                            text = " • $formattedDate",
                            fontSize = 12.sp,
                            color = Color(0xFF757575)
                        )
                    }
                }

                // Favorite button
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (record.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Star",
                        tint = if (record.isFavorite) StarAmber else Color(0xFF9E9E9E)
                    )
                }

                // Delete button
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFF757575)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Waveform scrubber
            WaveformView(
                amplitudes = amplitudes,
                progressFraction = progressFraction,
                onSeek = onSeek
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Timestamps row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val currentSeconds = currentPositionMs / 1000
                val totalSeconds = totalMs / 1000
                Text(
                    text = "%02d:%02d".format(currentSeconds / 60, currentSeconds % 60),
                    fontSize = 12.sp,
                    color = Color(0xFF757575)
                )
                Text(
                    text = "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60),
                    fontSize = 12.sp,
                    color = Color(0xFF757575)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playback controls (Rewind, Play/Pause, Forward)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSeekBackward, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Rewind 10s",
                        modifier = Modifier.size(32.dp),
                        tint = Color(0xFF3C4043)
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue)
                        .clickable { onTogglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(36.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                IconButton(onClick = onSeekForward, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Forward 10s",
                        modifier = Modifier.size(32.dp),
                        tint = Color(0xFF3C4043)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Speed selector chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                speeds.forEach { speed ->
                    val isSelected = currentSpeed == speed
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clickable { onSpeedChange(speed) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) PrimaryBlue else Color(0xFFF1F3F4)
                        )
                    ) {
                        Text(
                            text = "${speed}x",
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Color(0xFF3C4043),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
