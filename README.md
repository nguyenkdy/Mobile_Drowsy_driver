# Azure Blob Video Player - IoT Application

This Android application connects to Azure Blob Storage and continuously plays videos. Key features include:

- **Automatic Latest Video Detection**: Always plays the newest video from your Azure Blob container
- **Auto-Loop Playback**: Videos automatically repeat when they end
- **Real-Time Updates**: Monitors the blob container every 5 seconds for new videos
- **Instant Switching**: Automatically switches to a new video when it's uploaded to the blob
- **Landscape Mode**: Optimized for portrait/landscape IoT displays

## Prerequisites

1. **Azure Storage Account**
   - Create/have access to an Azure Storage Account
   - Create a blob container for your videos
   - Ensure the container allows **public/anonymous access** (or update the code to use SAS tokens)
   - Note your **connection string**

2. **Android Device/Emulator**
   - Android 8.0+ (API level 26+)
   - Internet connectivity

## Configuration Steps

### 1. Get Your Azure Connection String

1. Go to [Azure Portal](https://portal.azure.com)
2. Navigate to your Storage Account
3. Go to **Settings > Access keys**
4. Copy the **Connection string** (either key1 or key2)

### 2. Create/Use Blob Container

1. In your Storage Account, go to **Data storage > Containers**
2. Create a container (or use existing one)
3. Container must be set to allow **public access** (or update authentication method in code)
   - Settings > Change access level > Container (Anonymous read access for blobs)

### 3. Upload Test Videos

- Upload MP4 video files to your blob container
- Videos must have `.mp4` extension
- The app will play the most recently modified video

### 4. Run the App

1. Build and install the APK on your Android device
2. Launch the app
3. Enter the **Connection String** and **Container Name**
4. Tap **"Connect and Play"**
5. The app will start playing the latest video

## How It Works

1. **Initialization**: App connects to Azure Blob Storage using the provided connection string
2. **Video Discovery**: Scans container for `.mp4` files, sorted by modification time
3. **Playback**: Streams the latest video using ExoPlayer
4. **Looping**: Video automatically repeats when finished
5. **Monitoring**: Every 5 seconds, checks if a newer video has been uploaded
6. **Auto-Switch**: When a new video is detected, playback automatically switches

## Architecture

### Main Components

- **MainActivity.java**: Login/Configuration screen
  - Accepts connection string and container name
  - Saves configuration to SharedPreferences
  - Validates connection before launching player

- **VideoPlayerActivity.java**: Main playback screen
  - Uses ExoPlayer for video streaming
  - Implements repeat-all playback mode
  - Monitors for new videos every 5 seconds
  - Handles player lifecycle and errors

- **BlobStorageClient.java**: Azure Blob Storage integration
  - Lists blobs in container
  - Filters for MP4 files
  - Gets latest video metadata
  - Generates direct blob URLs for streaming

## Customization

### Change Monitoring Interval
Edit `VideoPlayerActivity.java`:
```java
private static final int MONITOR_INTERVAL = 5000; // Change to desired milliseconds
```

### Enable SAS Token Authentication
If your blob is private, update `BlobStorageClient.java` to generate SAS tokens:
```java
// Replace getLatestVideoUrl() to generate SAS URLs
```

### Change Landscape Orientation
In `AndroidManifest.xml`, remove or change:
```xml
android:screenOrientation="landscape"
```

## Troubleshooting

### "No videos found in container"
- Verify MP4 files exist in the container
- Ensure files have `.mp4` extension (case-sensitive)
- Check container access permissions

### "Connection failed"
- Verify connection string is correct
- Check internet connectivity
- Ensure container name is spelled correctly

### Video won't play
- Verify blob has public access
- Try uploading a different MP4 file
- Check that the GES (Graphics Execution Structure) is supported on your device

### App crashes after connecting
- Check logcat for detailed error messages
- Verify the MP4 file is not corrupted
- Ensure device has sufficient storage

## Security Considerations

⚠️ **Important**: Currently, this app stores the connection string in SharedPreferences (local device storage). For production:

1. Use **SAS tokens** instead of full connection strings
2. Implement **encrypted storage** for sensitive credentials
3. Set **blob-level access restrictions**
4. Use Azure AD authentication if possible
5. Implement **SSL/TLS** pinning

## Development Notes

- **Min API Level**: 26 (Android 8.0)
- **Target API Level**: 36
- **Dependencies**:
  - ExoPlayer 2.18.7 (video playback)
  - Azure Storage Blob SDK 12.14.1
  - Material Components for UI

## Example Video File Names

Good examples for testing:
- `routine_video_001.mp4`
- `stream_20260515_100001.mp4`
- `video_latest.mp4`

## Future Enhancements

- [ ] Support for multiple video formats (WebM, HLS)
- [ ] Cloud video recording integration
- [ ] Advanced scheduling/filtering
- [ ] Playlist management
- [ ] Multi-camera support
- [ ] Statistics and logging
- [ ] Secure credential storage

## License & Support

Built for IoT video streaming applications. Feel free to modify and extend for your needs.


