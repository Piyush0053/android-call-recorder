package com.callrecorder.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural Security Audit Test.
 * Guarantees that the app remains completely air-gapped with zero internet permissions,
 * and verifies that telephony and audio foreground service configurations meet Android security guidelines.
 */
class SecurityManifestAuditTest {

    @Test
    fun verifyAirGappedZeroNetworkPosture() {
        val manifestFile = File("src/main/AndroidManifest.xml").takeIf { it.exists() }
            ?: File("app/src/main/AndroidManifest.xml").takeIf { it.exists() }
            ?: File("D:/call recorder/app/src/main/AndroidManifest.xml")

        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())
        val manifestContent = manifestFile.readText()

        // Verify <uses-permission android:name="android.permission.INTERNET" /> is strictly absent
        assertFalse(
            "CRITICAL SECURITY VIOLATION: android.permission.INTERNET must NEVER be declared in this app!",
            manifestContent.contains("<uses-permission android:name=\"android.permission.INTERNET\"")
        )

        // Verify required telephony and audio permissions are declared
        assertTrue("Must request RECORD_AUDIO", manifestContent.contains("android.permission.RECORD_AUDIO"))
        assertTrue("Must request READ_PHONE_STATE", manifestContent.contains("android.permission.READ_PHONE_STATE"))
        assertTrue("Must request READ_CALL_LOG", manifestContent.contains("android.permission.READ_CALL_LOG"))

        // Verify foreground service types
        assertTrue(
            "CallRecordingService must declare foregroundServiceType with microphone|phoneCall",
            manifestContent.contains("microphone|phoneCall")
        )

        // Verify CallStateReceiver is registered
        assertTrue(
            "CallStateReceiver must register for PHONE_STATE intent",
            manifestContent.contains("android.intent.action.PHONE_STATE")
        )

        // Verify CallCompanionService is declared for true answer detection (Android 10+)
        assertTrue(
            "CallCompanionService must require BIND_INCALL_SERVICE",
            manifestContent.contains("android.permission.BIND_INCALL_SERVICE")
        )
        assertTrue(
            "CallCompanionService must register for InCallService intent",
            manifestContent.contains("android.telecom.InCallService")
        )

        // Verify ACTION_DIAL is declared for Android RoleManager dialer eligibility
        assertTrue(
            "MainActivity must declare ACTION_DIAL for RoleManager eligibility",
            manifestContent.contains("android.intent.action.DIAL")
        )
    }
}
