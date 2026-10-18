package com.callrecorder.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callrecorder.app.ui.theme.PrimaryBlue
import com.callrecorder.app.ui.theme.SecondaryTeal
import kotlin.math.abs

@Composable
fun ContactAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    isKnown: Boolean = true
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "#"
    val isRealName = isKnown && initial.firstOrNull()?.isLetter() == true

    val avatarBg = if (isRealName) {
        val hash = abs(name.hashCode())
        val colors = listOf(PrimaryBlue, SecondaryTeal, Color(0xFF673AB7), Color(0xFF009688), Color(0xFFFF5722), Color(0xFFE91E63))
        colors[hash % colors.size]
    } else {
        Color(0xFF546E7A) // Slate grey for unsaved numbers
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarBg),
        contentAlignment = Alignment.Center
    ) {
        if (isRealName) {
            Text(
                text = initial,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}
