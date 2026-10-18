# Automatic Call Recorder for Android

A privacy-focused, automatic call recording mobile application for Android (API 26–35+), designed to operate in **Companion Mode** alongside pre-installed dialers such as the **Google Phone app (`com.google.android.dialer`)**.

---

## Key Features

- **Automatic In-Call Initiation**: Automatically starts high-efficiency audio recording the instant a call connects (`CALL_STATE_OFFHOOK`), with zero manual action required.
- **Full Contact Coverage**: Automatically records calls from both saved contacts in the device phonebook and unknown/unsaved numbers.
- **Companion Mode with Google Phone**: Operates cleanly alongside the Google Phone app without replacing its dialer UI, modifying system files, or requiring device rooting.
- **Zero-Threat Air-Gapped Security**: Built with strictly **zero network access** (`android.permission.INTERNET` is completely omitted). Recordings never leave the physical device.
- **App-Scoped Internal Storage**: Audio files (`.m4a` AAC-LC) are stored strictly within the app's sandboxed private storage (`context.filesDir/recordings/`), isolated from third-party apps and file browsers.
- **Jetpack Compose Material 3 UI**:
  - Live filtering tabs: *All Calls*, *Known Contacts*, *Unknown Numbers*, and *Favorites*.
  - Real-time search by contact name, phone number digits, or notes.
  - Interactive canvas waveform playback scrubber with seek forward/backward (+10s / -10s).
  - Variable playback speed chips (`0.75x`, `1.0x`, `1.25x`, `1.5x`, `2.0x`).
  - Software Audio Gain slider to boost caller voice from earpiece/speaker.

---

## Architecture & Integration

### How It Interacts with the Phone & Google Dialer

```
[Inbound / Outbound Call]
         │
         ▼
[Google Phone App] (Displays dialer UI, user answers)
         │
         ▼
[Android Telephony Manager] (Fires CALL_STATE_OFFHOOK)
         │
         ▼
[CallStateReceiver & TelephonyCallback] (Intercepts connected state)
         │
         ▼
[CallRecordingService] (ForegroundService: microphone | phoneCall)
         │
         ├── AudioRecord (VOICE_COMMUNICATION + AEC/AGC)
         ├── AudioGainProcessor (Digital software amplification)
         └── AudioEncoder (MediaCodec -> AAC-LC .m4a)
         │
         ▼
[Call Terminated (CALL_STATE_IDLE)]
         │
         ▼
[Room Database & Scoped Storage] (Persists metadata & audio file)
```

---

## Project Structure

```
D:\call recorder\
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       └── gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/callrecorder/app/
        │   │   ├── CallRecorderApp.kt
        │   │   ├── core/
        │   │   │   ├── audio/
        │   │   │   │   ├── AudioEncoder.kt
        │   │   │   │   ├── AudioGainProcessor.kt
        │   │   │   │   ├── AudioPlayerEngine.kt
        │   │   │   │   ├── AudioRecorderEngine.kt
        │   │   │   │   └── WaveformExtractor.kt
        │   │   │   ├── storage/
        │   │   │   │   ├── StorageCleaner.kt
        │   │   │   │   └── StorageManager.kt
        │   │   │   ├── telephony/
        │   │   │   │   ├── CallRecordingService.kt
        │   │   │   │   ├── CallStateReceiver.kt
        │   │   │   │   ├── ContactLookupManager.kt
        │   │   │   │   └── ModernTelephonyCallback.kt
        │   │   │   └── utils/
        │   │   │       ├── BatteryOptimizationHelper.kt
        │   │   │       └── PermissionHelper.kt
        │   │   ├── data/
        │   │   │   ├── local/
        │   │   │   │   ├── CallRecordDao.kt
        │   │   │   │   ├── CallRecordDatabase.kt
        │   │   │   │   └── entity/
        │   │   │   │       ├── CallCategory.kt
        │   │   │   │       └── CallRecordEntity.kt
        │   │   │   └── repository/
        │   │   │       └── CallRecordRepositoryImpl.kt
        │   │   ├── domain/
        │   │   │   ├── model/
        │   │   │   │   ├── CallFilter.kt
        │   │   │   │   └── CallRecord.kt
        │   │   │   ├── repository/
        │   │   │   │   └── CallRecordRepository.kt
        │   │   │   └── usecase/
        │   │   │       ├── DeleteCallRecordUseCase.kt
        │   │   │       ├── GetCallRecordsUseCase.kt
        │   │   │       ├── SaveCallRecordUseCase.kt
        │   │   │       ├── SearchCallRecordsUseCase.kt
        │   │   │       ├── ToggleFavoriteUseCase.kt
        │   │   │       └── UpdateCallCategoryUseCase.kt
        │   │   └── ui/
        │   │       ├── MainActivity.kt
        │   │       ├── components/
        │   │       ├── navigation/
        │   │       ├── screens/
        │   │       │   ├── dashboard/
        │   │       │   ├── player/
        │   │       │   ├── search/
        │   │       │   └── settings/
        │   │       └── theme/
        │   └── res/
        └── test/
            └── java/com/callrecorder/app/
                ├── AudioGainProcessorTest.kt
                ├── ContactLookupManagerTest.kt
                ├── SecurityManifestAuditTest.kt
                └── StorageManagerTest.kt
```

---

## Building and Running the App

### Requirements
- **Android Studio** (Hedgehog 2023.1.1 or Ladybug / Koala 2024+)
- **JDK 17**
- **Android SDK** with platform `android-35` installed

### Steps:
1. Open **Android Studio**.
2. Select **File -> Open...** and navigate to `D:\call recorder`.
3. Allow Gradle to sync the project.
4. Connect an Android phone or launch an Android Emulator (API 26 to 35).
5. Click **Run 'app'** (`Shift + F10`).
6. Upon first launch, grant the prompted telephony, contact, and microphone permissions.
7. From **Settings**, grant the **Battery Optimization Exemption** so the background recording service remains active during incoming calls.
