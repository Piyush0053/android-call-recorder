package com.callrecorder.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.callrecorder.app.ui.theme.PrimaryBlue

@Composable
fun CategoryChip(
    label: String,
    isSelected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isSelected,
        onClick = onSelected,
        label = { Text(text = label) },
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PrimaryBlue.copy(alpha = 0.15f),
            selectedLabelColor = PrimaryBlue,
            containerColor = Color(0xFFF1F3F4),
            labelColor = Color(0xFF3C4043)
        ),
        border = null,
        modifier = modifier.padding(end = 8.dp)
    )
}
