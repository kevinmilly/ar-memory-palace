# Daily Spatial Walkthrough System - User Guide

## Overview

Your AR Memory Palace app has been refactored into a **"Daily Spatial Walkthrough"** system that uses **room-based relative positioning** instead of Cloud Anchors. Notes now persist indefinitely using local ARCore mapping and relative poses.

## Key Changes

### ✅ What's New
- **Room-Based System**: Organize notes by rooms (Kitchen, Bathroom, Living Room, Office, Bedroom)
- **Local Persistence**: Notes persist forever using relative positioning (no Cloud Anchors, no TTL limits)
- **Align Room Feature**: Set a room origin once, then all notes appear at correct positions
- **Offline-First**: Works completely offline, no internet required after initial Firebase sync
- **No Billing Required**: Zero cloud costs, no API limits, no OAuth complexity

### ❌ What's Removed
- Cloud Anchors (hostCloudAnchorWithTtl, resolveCloudAnchor)
- Cloud Anchor OAuth/keyless authentication
- 24h/365-day TTL limitations
- CloudAnchorAuth.kt file
- Cloud Anchor API metadata

## How It Works

### Technical Architecture

1. **Room Origin Anchor**: Each room has ONE origin anchor set manually by the user
2. **Relative Poses**: Notes are saved relative to the room origin using pose mathematics
3. **Pose Composition**: When loading, the app composes world poses from: `worldPose = originPose.compose(relativePose)`
4. **ARCore Mapping**: ARCore's local mapping tracks surfaces, the app adds logical room groupings

### Data Model

```kotlin
data class Note(
    // Room-based positioning (NEW)
    val roomId: String = "",              // e.g., "Kitchen", "Office"
    val localPosX: Float = 0f,            // Relative position from room origin
    val localPosY: Float = 0f,
    val localPosZ: Float = 0f,
    val localRotX: Float = 0f,            // Relative rotation quaternion
    val localRotY: Float = 0f,
    val localRotZ: Float = 0f,
    val localRotW: Float = 1f,
    
    // Legacy fields (for backwards compatibility)
    val cloudAnchorId: String = "",       // Deprecated, not used
    val positionX: Float = 0f,            // Migration fallback
    val positionY: Float = 0f,
    val positionZ: Float = 0f,
    
    // Standard fields
    val id: String = "",
    val userId: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val audioUrl: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
```

## User Workflow

### First-Time Setup (Per Room)

1. **Select a Room**
   - Open menu (⋮) → Select Room → Choose "Kitchen"
   
2. **Align Room**
   - Tap menu → "Align Room"
   - App prompts: "Tap a surface to set room origin for Kitchen"
   - Tap any surface (table, counter, floor) as the reference point
   - App confirms: "Room origin set for Kitchen! 📍"

3. **Place Notes**
   - Tap on surfaces to place notes (text, image, or audio)
   - Each note is saved relative to the room origin
   - Notes appear immediately after placement

### Daily Usage (Returning to a Room)

1. **Select the Room**
   - Open menu → Select Room → "Kitchen"
   - App clears any visible notes from other rooms

2. **Align Room**
   - Tap menu → "Align Room"
   - Tap the SAME surface you used as the origin before
   - App loads all notes for Kitchen and places them at correct positions
   - All your notes appear exactly where you placed them!

### Tips for Best Results

✅ **DO:**
- Use a distinctive, stable surface as room origin (e.g., kitchen counter corner, desk edge)
- Point your phone at the same surface/angle when re-aligning
- Wait for ARCore to detect planes before aligning (look for grid patterns)
- Place notes on well-lit surfaces with good texture

❌ **DON'T:**
- Don't use movable objects as room origin (e.g., chairs, laptops)
- Don't change lighting dramatically between sessions
- Don't rush - give ARCore time to map the environment
- Don't place room origin in featureless areas (blank walls)

## Menu Options

### Align Room
**Purpose**: Set or reset the room origin anchor  
**When to Use**: First time in a room, or when returning to restore notes  
**Action**: Tap this, then tap a reference surface

### Start Walkthrough
**Purpose**: Shows instructions for the spatial walkthrough system  
**When to Use**: First time using the app or when you forget the workflow  
**Action**: Displays a dialog with setup instructions

### Select Room
**Rooms Available**:
- Kitchen
- Bathroom
- Living Room
- Office
- Bedroom

**Action**: Switches active room, clears visible notes, prompts to align room

### Replay Tutorial
**Purpose**: Shows the AR tutorial overlay again  
**Action**: Replays the 3-step tutorial (Detect planes → Tap surface → View notes)

### Sign In / Sign Out
**Purpose**: Firebase authentication (required for saving notes)  
**Action**: Sign in with Google or use anonymous authentication

## Technical Details

### Pose Mathematics

When **saving a note**:
```kotlin
val anchorPose = anchor.pose              // Note's world position
val originPose = roomOriginAnchor.pose    // Room origin world position
val relativePose = originPose.inverse().compose(anchorPose)

// Extract translation and rotation
val translation = relativePose.translation  // [x, y, z]
val rotation = relativePose.rotationQuaternion  // [x, y, z, w]
```

When **loading notes**:
```kotlin
val originPose = roomOriginAnchor.pose
val localPose = Pose(localTranslation, localRotation)
val worldPose = originPose.compose(localPose)

val anchor = session.createAnchor(worldPose)
```

### Backwards Compatibility

Old notes from the Cloud Anchor system are migrated automatically:
- If `roomId` is empty but `positionX/Y/Z` exists → Creates anchor using world position
- Displays toast: "Legacy note loaded - realign to update"
- User can re-save notes to update them to room-relative system

### AR Session Configuration

```kotlin
session = Session(this).apply {
    val config = Config(this).apply {
        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        focusMode = Config.FocusMode.AUTO
        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
        lightEstimationMode = Config.LightEstimationMode.DISABLED
        // Cloud Anchors disabled - using local room-relative positioning
    }
    configure(config)
}
```

## Troubleshooting

### Notes Don't Appear After Align Room

**Possible Causes**:
1. Different room selected than where notes were placed
2. Room origin set at different location than before
3. ARCore hasn't detected planes yet
4. Poor lighting or featureless surfaces

**Solutions**:
- Verify correct room is selected in menu
- Try tapping the exact same spot you used as origin before
- Wait for ARCore plane detection (look for grid overlay)
- Improve lighting and point camera at textured surfaces

### Notes Appear in Wrong Positions

**Possible Causes**:
1. Room origin set at different location
2. ARCore tracking lost during session
3. Environment changed significantly (furniture moved)

**Solutions**:
- Use a stable, distinctive landmark as room origin
- Re-align room origin at the same reference point
- Ensure good lighting and texture in the environment

### "No saved notes found in [Room]"

**Possible Causes**:
1. No notes have been placed in this room yet
2. Notes were placed in a different room
3. Firebase sync hasn't completed

**Solutions**:
- Check if you're in the correct room
- Wait a few seconds for Firebase to sync
- Place a test note to verify the room works

## Implementation Checklist

✅ Removed Cloud Anchors completely  
✅ Removed cloudAnchorMode from AR session config  
✅ Extended Note model with room-based fields  
✅ Implemented relative pose capture on save  
✅ Implemented pose composition on load  
✅ Added room selection menu (5 rooms)  
✅ Added "Align Room" feature  
✅ Added "Start Walkthrough" instructions  
✅ Removed CloudAnchorAuth.kt  
✅ Backwards compatibility for legacy notes  
✅ Cleared all Cloud Anchor error handling  

## Future Enhancements

Potential improvements to consider:

1. **Room Origin Persistence**: Save room origin positions to Firebase for cross-device sync
2. **Visual Room Origin Marker**: Show a permanent marker at the room origin
3. **Automatic Re-alignment**: Use ARCore's Visual Positioning System for automatic origin detection
4. **Multi-Origin Rooms**: Support multiple reference points in large rooms
5. **Room Templates**: Pre-defined room layouts for common spaces
6. **3D Floor Plans**: Generate floor plans based on placed notes and origins
7. **Export/Import**: Share room configurations between devices

## Support

For issues or questions, check:
- Firebase Console for authentication/database errors
- Android Logcat for ARCore errors
- README.md for setup instructions

---

**System Version**: Room-Based Spatial Walkthrough v1.0  
**Last Updated**: December 3, 2025  
**Compatibility**: ARCore 1.41.0+, Android 7.0+ (API 24+)
