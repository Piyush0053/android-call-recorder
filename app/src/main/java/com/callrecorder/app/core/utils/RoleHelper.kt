package com.callrecorder.app.core.utils

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log

/**
 * Helper to manage Android Telecom Call Companion / Dialer Role.
 * This role allows CallCompanionService to receive InCallService callbacks and detect
 * true call answer events (Call.STATE_ACTIVE) directly from Android Telecom.
 */
object RoleHelper {

    private const val TAG = "RoleHelper"

    fun isCallCompanionHeld(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) ?: false
            } else {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                telecomManager?.defaultDialerPackage == context.packageName
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking role held: ${e.message}")
            false
        }
    }

    fun requestCallCompanionRoleIntent(context: Context): Intent? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    Log.i(TAG, "Creating RoleManager request intent for ROLE_DIALER")
                    return roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                }
            }

            // Universal fallback for Android Telecom
            Log.i(TAG, "Using TelecomManager fallback for default dialer request")
            return Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating role request intent: ${e.message}", e)
            return try {
                Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            } catch (_: Exception) {
                null
            }
        }
    }
}
