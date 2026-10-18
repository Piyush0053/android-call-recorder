package com.callrecorder.app.ui.screens.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.core.audio.AudioPlayerEngine
import com.callrecorder.app.domain.model.CallFilter
import com.callrecorder.app.domain.model.CallRecord
import com.callrecorder.app.domain.usecase.DeleteCallRecordUseCase
import com.callrecorder.app.domain.usecase.GetCallRecordsUseCase
import com.callrecorder.app.domain.usecase.ToggleFavoriteUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel managing the state for the Call Recorder dashboard.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as CallRecorderApp
    private val repository = app.repository
    val audioPlayerEngine: AudioPlayerEngine = app.audioPlayerEngine

    private val getCallRecordsUseCase = GetCallRecordsUseCase(repository)
    private val toggleFavoriteUseCase = ToggleFavoriteUseCase(repository)
    private val deleteCallRecordUseCase = DeleteCallRecordUseCase(repository)

    // Tab state: 0 = All, 1 = Known Contacts, 2 = Unknown Numbers, 3 = Favorites
    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    private val _filter = MutableStateFlow(CallFilter())

    val recordings: StateFlow<List<CallRecord>> = _filter.flatMapLatest { filter ->
        getCallRecordsUseCase(filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _hasAllPermissions = MutableStateFlow(
        com.callrecorder.app.core.utils.PermissionHelper.hasAllPermissions(application)
    )
    val hasAllPermissions: StateFlow<Boolean> = _hasAllPermissions.asStateFlow()

    private val _hasOverlayPermission = MutableStateFlow(
        com.callrecorder.app.core.utils.PermissionHelper.hasOverlayPermission(application)
    )
    val hasOverlayPermission: StateFlow<Boolean> = _hasOverlayPermission.asStateFlow()

    private val _isCompanionRoleHeld = MutableStateFlow(
        com.callrecorder.app.core.utils.RoleHelper.isCallCompanionHeld(application)
    )
    val isCompanionRoleHeld: StateFlow<Boolean> = _isCompanionRoleHeld.asStateFlow()

    private var contactsObserver: android.database.ContentObserver? = null

    init {
        viewModelScope.launch {
            repository.cleanDuplicateRecordings()
            repository.syncContactsAndCallLogs(getApplication())
        }
        registerContentObserver()
    }

    private fun registerContentObserver() {
        try {
            val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    syncContacts()
                }
            }
            contactsObserver = observer
            getApplication<Application>().contentResolver.registerContentObserver(
                android.provider.ContactsContract.Contacts.CONTENT_URI,
                true,
                observer
            )
            getApplication<Application>().contentResolver.registerContentObserver(
                android.provider.CallLog.Calls.CONTENT_URI,
                true,
                observer
            )
        } catch (_: Exception) {}
    }

    fun syncContacts() {
        viewModelScope.launch {
            repository.syncContactsAndCallLogs(getApplication())
        }
    }

    fun refreshPermissions() {
        _hasAllPermissions.value = com.callrecorder.app.core.utils.PermissionHelper.hasAllPermissions(getApplication())
        _hasOverlayPermission.value = com.callrecorder.app.core.utils.PermissionHelper.hasOverlayPermission(getApplication())
        _isCompanionRoleHeld.value = com.callrecorder.app.core.utils.RoleHelper.isCallCompanionHeld(getApplication())
        viewModelScope.launch {
            repository.cleanDuplicateRecordings()
            repository.syncContactsAndCallLogs(getApplication())
        }
    }

    override fun onCleared() {
        super.onCleared()
        contactsObserver?.let {
            try {
                getApplication<Application>().contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {}
        }
    }

    private val _activePlayingRecord = MutableStateFlow<CallRecord?>(null)
    val activePlayingRecord: StateFlow<CallRecord?> = _activePlayingRecord.asStateFlow()

    private val _selectedRecordForSheet = MutableStateFlow<CallRecord?>(null)
    val selectedRecordForSheet: StateFlow<CallRecord?> = _selectedRecordForSheet.asStateFlow()

    fun selectTab(index: Int) {
        _selectedTabIndex.value = index
        _filter.value = when (index) {
            1 -> CallFilter(onlyKnown = true)
            2 -> CallFilter(onlyUnknown = true)
            3 -> CallFilter(onlyFavorites = true)
            else -> CallFilter()
        }
    }

    fun playRecord(record: CallRecord) {
        _activePlayingRecord.value = record
        val file = File(record.filePath)
        audioPlayerEngine.playFile(file, getApplication())
    }

    fun openPlayerSheet(record: CallRecord) {
        _selectedRecordForSheet.value = record
        if (_activePlayingRecord.value?.id != record.id) {
            playRecord(record)
        }
    }

    fun closePlayerSheet() {
        _selectedRecordForSheet.value = null
    }

    fun togglePlayPause(record: CallRecord) {
        if (_activePlayingRecord.value?.id == record.id) {
            audioPlayerEngine.togglePlayPause()
        } else {
            playRecord(record)
        }
    }

    fun seekToFraction(fraction: Float) {
        val totalMs = audioPlayerEngine.durationMs.value
        if (totalMs > 0) {
            val targetMs = (totalMs * fraction).toInt()
            audioPlayerEngine.seekTo(targetMs)
        }
    }

    fun seekForward() = audioPlayerEngine.seekForward(10_000)

    fun seekBackward() = audioPlayerEngine.seekBackward(10_000)

    fun setSpeed(speed: Float) = audioPlayerEngine.setSpeed(speed)

    fun toggleFavorite(record: CallRecord) {
        viewModelScope.launch {
            toggleFavoriteUseCase(record.id, !record.isFavorite)
        }
    }

    fun deleteRecord(record: CallRecord) {
        viewModelScope.launch {
            if (_activePlayingRecord.value?.id == record.id) {
                audioPlayerEngine.stop()
                _activePlayingRecord.value = null
            }
            if (_selectedRecordForSheet.value?.id == record.id) {
                _selectedRecordForSheet.value = null
            }
            deleteCallRecordUseCase(record.id)
        }
    }
}
