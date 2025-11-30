# AR Notes Android - Setup Instructions

## Prerequisites
1. Install Android Studio (latest version)
2. Install ARCore on your Pixel 9 Pro from Play Store
3. Enable Developer Options on your phone
4. Enable USB Debugging

## Build & Install Steps

### Option 1: Using Android Studio (Recommended)
1. Open Android Studio
2. Click "Open" and select the `ar-notes-android` folder
3. Wait for Gradle sync to complete
4. Connect your Pixel 9 Pro via USB
5. Click the green "Run" button (or press Shift+F10)
6. Select your Pixel 9 Pro from the device list
7. App will install and launch automatically

### Option 2: Command Line Build
```bash
cd /workspaces/ar-memory-palace/ar-notes-android

# Build debug APK
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug

# Or build and install in one command
./gradlew installDebug
```

The APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Option 3: Manual APK Installation
1. Build the APK using Android Studio or Gradle
2. Copy `app-debug.apk` to your phone
3. Open the file on your phone
4. Allow installation from unknown sources if prompted
5. Install and launch

## First Run
1. Grant camera permission when prompted
2. ARCore will verify device compatibility
3. Point camera at a flat surface
4. Tap screen to place AR notes (green cubes)
5. Notes will persist in 3D space as you move around

## Features
- ✅ Native ARCore integration
- ✅ Hit-test for placing objects
- ✅ Real-time AR rendering
- ✅ Camera permission handling
- ✅ ARCore compatibility checks
- ⏳ Firebase cloud anchors (coming next)
- ⏳ Note text input
- ⏳ Multiple note colors

## Troubleshooting

**"ARCore not supported"**
- Make sure ARCore app is installed from Play Store
- Update ARCore to latest version

**"Camera permission denied"**
- Go to Settings > Apps > AR Memory Palace > Permissions
- Enable Camera permission

**Build errors in Codespaces**
- This project requires Android Studio on a local machine
- Codespaces doesn't support Android emulators
- Transfer the project to your local machine

## Next Steps
Once the basic app works, we can add:
1. Text input for notes
2. Color picker for different note types
3. Firebase sync for persistent storage
4. Cloud anchors for multi-device sharing
5. Delete/edit functionality
