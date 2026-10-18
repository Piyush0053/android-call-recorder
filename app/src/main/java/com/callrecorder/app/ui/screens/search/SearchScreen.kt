package com.callrecorder.app.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.callrecorder.app.domain.model.CallFilter
import com.callrecorder.app.domain.model.CallRecord
import com.callrecorder.app.ui.components.EmptyState
import com.callrecorder.app.ui.screens.dashboard.DashboardViewModel
import com.callrecorder.app.ui.screens.dashboard.RecordingCard
import com.callrecorder.app.ui.screens.player.PlayerBottomSheet
import com.callrecorder.app.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: DashboardViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val allRecordings by viewModel.recordings.collectAsState()
    val activePlayingRecord by viewModel.activePlayingRecord.collectAsState()
    val selectedRecordForSheet by viewModel.selectedRecordForSheet.collectAsState()

    val isPlaying by viewModel.audioPlayerEngine.isPlaying.collectAsState()
    val currentPositionMs by viewModel.audioPlayerEngine.currentPositionMs.collectAsState()
    val durationMs by viewModel.audioPlayerEngine.durationMs.collectAsState()
    val currentSpeed by viewModel.audioPlayerEngine.currentSpeed.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val searchResults = remember(searchQuery, allRecordings) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            val q = searchQuery.trim().lowercase()
            allRecordings.filter { record ->
                (record.contactName?.lowercase()?.contains(q) == true) ||
                record.phoneNumber.contains(q) ||
                (record.notes?.lowercase()?.contains(q) == true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Search Recordings") },
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
        ) {
            // Search Input Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search by name, phone number, or notes...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = PrimaryBlue
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (searchQuery.isBlank()) {
                EmptyState(
                    title = "Type to Search",
                    description = "Enter any contact name, phone number digits, or saved notes to locate recordings instantly.",
                    modifier = Modifier.weight(1f)
                )
            } else if (searchResults.isEmpty()) {
                EmptyState(
                    title = "No Matches Found",
                    description = "No recorded conversations matched \"$searchQuery\". Try checking the spelling or number.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(
                        items = searchResults,
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
    }
}
