package com.callrecorder.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
fun RecordingCard(
    record: CallRecord,
    isCurrentlyPlaying: Boolean,
    onCardClick: () -> Unit,
    onPlayClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault()) }
    val formattedDate = remember(record.timestamp) { dateFormat.format(Date(record.timestamp)) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Contact Avatar & Name Resolution
            val isKnown = record.isKnownContact && !record.contactName.isNullOrBlank()
            val headerTitle = if (isKnown) {
                record.contactName!!
            } else {
                record.phoneNumber.takeIf { it.isNotBlank() && !it.equals("Unknown Number", ignoreCase = true) } ?: "Private Number"
            }

            ContactAvatar(
                name = headerTitle,
                isKnown = isKnown,
                size = 46.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            // Details Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
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
                    Icon(
                        imageVector = if (record.callType == CallType.INCOMING) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = if (record.callType == CallType.INCOMING) IncomingGreen else OutgoingBlue
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$formattedDate • ${record.formattedDuration}",
                        fontSize = 12.sp,
                        color = Color(0xFF757575)
                    )
                }
            }

            // Favorite star
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (record.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Favorite",
                    modifier = Modifier.size(20.dp),
                    tint = if (record.isFavorite) StarAmber else Color(0xFFBDBDBD)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Quick Play Button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isCurrentlyPlaying) PrimaryBlue else Color(0xFFE3F2FD))
                    .clickable { onPlayClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCurrentlyPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isCurrentlyPlaying) "Pause" else "Play",
                    modifier = Modifier.size(22.dp),
                    tint = if (isCurrentlyPlaying) Color.White else PrimaryBlue
                )
            }
        }
    }
}
