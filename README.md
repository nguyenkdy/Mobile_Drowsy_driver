
# MyIoTApplication — Drowsiness detection for driver

This Android app (package `com.example.myiotapplication`) is a simple IoT-oriented video player that:

- Streams the latest .mp4 video from an Azure Blob Storage container using the Azure Java SDK
- Continuously loops the routine video (ExoPlayer repeat mode)
- Polls the container periodically (every 5 seconds by default) and automatically switches to a newer video when found
- Provides a separate "Live Stream" screen that plays an HLS stream (Cloudflare) and is opened/closed by a Firebase Realtime Database flag

The app is implemented in Java and the main runtime files are under `app/src/main/java/com/example/myiotapplication/`:

- `VideoPlayerActivity.java` — launcher activity. Plays the latest routine video and monitors for updates.
- `LiveStreamActivity.java` — plays a Cloudflare HLS live stream.
- `BlobStorageClient.java` — Azure Blob Storage helper: lists blobs, filters for `.mp4`, returns latest blob and its URL.
- `AppConfig.java` — simple, hard-coded configuration constants for quick testing (connection string, container name, live stream URL).

Important build info (from `app/build.gradle.kts`): minSdk 26, targetSdk 36. Key libraries include ExoPlayer and the Azure Storage Blob SDK.

Quick checklist

- [x] Update `AppConfig.java` with your Azure connection string and container name for testing
- [x] Optionally update `CLOUDFLARE_LIVE_STREAM_URL` in `AppConfig.java` for HLS live playback
- [x] Open the project in Android Studio (recommended) or build via Gradle

Prerequisites

- Android Studio or a Java 11 SDK + Android SDK (compileSdk 36)
- An Android device or emulator (API level 26+)
- An Azure Storage account and a blob container containing `.mp4` files (for quick testing the container can be public)
- (Optional) Firebase Realtime Database URL used by the app: `https://my-iot-project-658d2-default-rtdb.asia-southeast1.firebasedatabase.app/` — the code listens at `iot_status/is_sleeping` to show/close Live Stream UI

Configuration (quick)

1. Edit `app/src/main/java/com/example/myiotapplication/AppConfig.java` and set:

   - `AZURE_CONNECTION_STRING` — your storage account connection string (for quick testing only)
   - `CONTAINER_NAME` — the blob container name that contains `.mp4` files
   - `CLOUDFLARE_LIVE_STREAM_URL` — HLS URL to play in `LiveStreamActivity` (optional)

   Note: The app currently uses the connection string directly in code. Do not include production secrets in source control.

2. Ensure your container contains `.mp4` files. The app chooses the newest by creation/modified time.

Build & Run (Windows, PowerShell examples)

Open a PowerShell in the project root (`D:\MyApplicationIoT`) and run:

```powershell
.
# Build debug APK
.\gradlew assembleDebug

# Install on a connected device (requires adb on PATH)
.\gradlew installDebug

# Or open the project with Android Studio and Run/Debug as usual
```

Behavior details

- The launcher activity is `VideoPlayerActivity` (declared as MAIN/LAUNCHER in `AndroidManifest.xml`) and is locked to landscape by default (`android:screenOrientation="landscape"`).
- `VideoPlayerActivity`:
  - Creates a `BlobStorageClient` with values from `AppConfig` and uses `getLatestVideo()`/`getLatestVideoUrl()` to stream the newest `.mp4` with ExoPlayer.
  - Uses `player.setRepeatMode(Player.REPEAT_MODE_ALL)` so the routine video loops.
  - Monitors for new videos by calling `containerClient.listBlobs()` via `BlobStorageClient.isNewVideoAvailable(...)` every 5 seconds (see `startRoutineMonitoring()` in `VideoPlayerActivity.java`).
  - Shows a "Alert/Live" button when the Firebase flag `iot_status/is_sleeping` is true. Pressing it opens `LiveStreamActivity`.
- `LiveStreamActivity`:
  - Plays an HLS stream using ExoPlayer (HlsMediaSource) and closes automatically when Firebase signals the alert cleared.

Where to change common settings

- Change the monitoring interval: edit the delay in `VideoPlayerActivity.startRoutineMonitoring()` — currently `monitorHandler.postDelayed(monitorRunnable, 5000);` (5000ms).
- Change Azure credentials: `AppConfig.java`.
- Change live stream URL: `AppConfig.java` (CLOUDFLARE_LIVE_STREAM_URL).
- Change orientation: `AndroidManifest.xml` (remove or edit `android:screenOrientation` on `VideoPlayerActivity`).

