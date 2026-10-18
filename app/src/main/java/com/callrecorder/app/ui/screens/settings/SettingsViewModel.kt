package com.callrecorder.app.ui.screens.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.core.utils.BatteryOptimizationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as CallRecorderApp
    private val prefs = application.getSharedPreferences("call_recorder_settings", Context.MODE_PRIVATE)

    private val _isAutoRecordEnabled = MutableStateFlow(prefs.getBoolean("auto_record_enabled", true))
    val isAutoRecordEnabled: StateFlow<Boolean> = _isAutoRecordEnabled.asStateFlow()

    private val _isRecordUnknownEnabled = MutableStateFlow(prefs.getBoolean("record_unknown_enabled", true))
    val isRecordUnknownEnabled: StateFlow<Boolean> = _isRecordUnknownEnabled.asStateFlow()

    private val _gainMultiplier = MutableStateFlow(prefs.getFloat("gain_multiplier", 2.2f))
    val gainMultiplier: StateFlow<Float> = _gainMultiplier.asStateFlow()

    val totalStorageBytes: StateFlow<Long> = app.repository.getTotalStorageUsed().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0L
    )

    private val _isBatteryExempt = MutableStateFlow(
        BatteryOptimizationHelper.isIgnoringBatteryOptimizations(application)
    )
    val isBatteryExempt: StateFlow<Boolean> = _isBatteryExempt.asStateFlow()

    private val _isOverlayGranted = MutableStateFlow(
        com.callrecorder.app.core.utils.PermissionHelper.hasOverlayPermission(application)
    )
    val isOverlayGranted: StateFlow<Boolean> = _isOverlayGranted.asStateFlow()

    private val _isSpeakerphoneBoostEnabled = MutableStateFlow(
        prefs.getBoolean("speakerphone_boost_enabled", true)
    )
    val isSpeakerphoneBoostEnabled: StateFlow<Boolean> = _isSpeakerphoneBoostEnabled.asStateFlow()

    private val _isCompanionRoleHeld = MutableStateFlow(
        com.callrecorder.app.core.utils.RoleHelper.isCallCompanionHeld(application)
    )
    val isCompanionRoleHeld: StateFlow<Boolean> = _isCompanionRoleHeld.asStateFlow()

    fun setAutoRecordEnabled(enabled: Boolean) {
        _isAutoRecordEnabled.value = enabled
        prefs.edit().putBoolean("auto_record_enabled", enabled).apply()
    }

    fun setRecordUnknownEnabled(enabled: Boolean) {
        _isRecordUnknownEnabled.value = enabled
        prefs.edit().putBoolean("record_unknown_enabled", enabled).apply()
    }

    fun setGainMultiplier(gain: Float) {
        _gainMultiplier.value = gain
        prefs.edit().putFloat("gain_multiplier", gain).apply()
    }

    fun setSpeakerphoneBoostEnabled(enabled: Boolean) {
        _isSpeakerphoneBoostEnabled.value = enabled
        prefs.edit().putBoolean("speakerphone_boost_enabled", enabled).apply()
    }

    fun refreshCompanionRoleStatus() {
        _isCompanionRoleHeld.value = com.callrecorder.app.core.utils.RoleHelper.isCallCompanionHeld(getApplication())
    }

    fun refreshBatteryOptimizationStatus() {
        _isBatteryExempt.value = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(getApplication())
    }

    fun requestBatteryOptimizationExemption() {
        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(getApplication())
        refreshBatteryOptimizationStatus()
    }

    fun refreshOverlayStatus() {
        _isOverlayGranted.value = com.callrecorder.app.core.utils.PermissionHelper.hasOverlayPermission(getApplication())
    }

    fun requestOverlayPermission() {
        com.callrecorder.app.core.utils.PermissionHelper.requestOverlayPermission(getApplication())
        refreshOverlayStatus()
    }
}
