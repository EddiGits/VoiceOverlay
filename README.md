# VoiceOverlay - Floating Voice Transcription App

A lightweight Android app that provides a floating overlay button for quick voice recording and transcription using OpenAI's Whisper API.

## Features

- **Floating Overlay Button**: Always-accessible microphone button that stays on top of other apps
- **Voice Recording**: Tap to start recording, tap again to stop
- **Whisper AI Transcription**: Automatic speech-to-text using OpenAI Whisper API
- **Clipboard Integration**: Transcribed text is automatically copied to clipboard
- **Draggable Button**: Move the floating button anywhere on screen
- **Simple Configuration**: Easy settings screen for API credentials

## Screenshots

The app displays a floating green microphone button that:
- Turns **red** when recording
- Turns **green** when stopped
- Can be dragged to any position on screen

## Setup

### Prerequisites

- Android device (API 17+, Android 4.2+)
- OpenAI API key with Whisper access
- Termux (for building from source)

### Installation

1. **Install the APK**:
   - Build from source using `./build.sh`
   - Install the generated `final.apk`

2. **Grant Permissions**:
   ```bash
   adb shell pm grant com.voiceoverlay android.permission.SYSTEM_ALERT_WINDOW
   adb shell pm grant com.voiceoverlay android.permission.RECORD_AUDIO
   ```

3. **Configure API Settings**:
   - Open the VoiceOverlay app
   - Enter your Whisper API URL: `https://api.openai.com/v1/audio/transcriptions`
   - Enter your OpenAI API key
   - Tap "Save Settings"

4. **Enable Overlay Permission**:
   - Tap "Enable Overlay Permission" in the app
   - Grant permission in Android settings

5. **Start the Service**:
   - Tap "Start Voice Overlay"
   - The floating button will appear on your screen

## Usage

1. **Start Recording**: Tap the floating green button - it turns red
2. **Speak**: Say what you want to transcribe
3. **Stop & Transcribe**: Tap the red button again
4. **Get Text**: The transcription is automatically copied to your clipboard
5. **Paste**: Long-press any text field and paste the transcribed text

## Building from Source

### Requirements

- Termux on Android
- `build-essential` package
- `aapt2`, `dx`, `zipalign`, `apksigner` (Android build tools)
- Java JDK

### Build Steps

```bash
cd VoiceOverlay
./build.sh
```

The APK will be generated as `final.apk`.

### Build Process

The build script performs:
1. Resource compilation with aapt2
2. Resource linking
3. Java compilation (targeting Java 8)
4. DEX conversion
5. APK packaging with uncompressed resources.arsc
6. Zipalign optimization
7. APK signing

## Project Structure

```
VoiceOverlay/
├── src/com/voiceoverlay/
│   ├── MainActivity.java          # Settings screen
│   ├── OverlayService.java        # Floating button service
│   ├── AudioRecorder.java         # Audio recording (M4A format)
│   └── WhisperAPI.java           # OpenAI Whisper API client
├── res/
│   ├── values/strings.xml
│   ├── drawable/ic_launcher.xml
│   └── xml/network_security_config.xml
├── AndroidManifest.xml
├── build.sh                       # Fast build script (5-10 seconds)
└── README.md
```

## Permissions

- `RECORD_AUDIO` - For voice recording
- `INTERNET` - For API calls to Whisper
- `SYSTEM_ALERT_WINDOW` - For floating overlay
- `ACCESS_NETWORK_STATE` - For network connectivity checks
- `ACCESS_WIFI_STATE` - For WiFi network binding

## Technical Details

- **Audio Format**: M4A (AAC codec, 44.1kHz, 128kbps)
- **Network**: Direct binding to active network for reliable DNS resolution
- **API**: Multipart form-data upload to Whisper API
- **Minimum SDK**: 17 (Android 4.2)
- **Target SDK**: 30 (Android 11)

## Troubleshooting

### DNS Resolution Issues

If you encounter "Unable to resolve host" errors:

1. Check network settings: Settings → Apps → Voice Overlay → Mobile data & Wi-Fi
2. Ensure unrestricted data usage is enabled
3. Disable Private DNS: Settings → Network & internet → Private DNS → Off
4. Restart WiFi:
   ```bash
   adb shell "svc wifi disable && sleep 2 && svc wifi enable"
   ```

### Button Not Responding

- Make sure overlay permission is granted
- Check that the service is running (look for the floating button)
- Try restarting the service from the app

### Recording Not Working

- Verify microphone permission is granted
- Check that other apps can record audio
- Try force-stopping and restarting the app

## Credits

Built with Termux using the manual Android build pipeline documented in the companion FastKeyboard project.

## License

MIT License - Feel free to use and modify.

---

**Note**: This app requires an OpenAI API key. API usage incurs costs based on OpenAI's pricing for Whisper API calls.
