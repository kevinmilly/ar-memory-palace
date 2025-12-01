# AR Memory Palace

Android AR app for creating spatial notes with text, images, and audio using ARCore Cloud Anchors for world-locked positioning.

## Setup Requirements

### 1. Google Cloud Platform API Key (Required for Cloud Anchors)

Cloud Anchors enable notes to be "stuck" to exact real-world locations.

**Steps:**
1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Create a new project or select existing
3. Enable **ARCore API**:
   - Navigate to "APIs & Services" → "Library"
   - Search for "ARCore API"
   - Click "Enable"
4. Create API Key:
   - Go to "APIs & Services" → "Credentials"
   - Click "Create Credentials" → "API Key"
   - Copy the API key
5. **Update AndroidManifest.xml**:
   ```xml
   <meta-data
       android:name="com.google.android.ar.API_KEY"
       android:value="YOUR_ACTUAL_API_KEY" />
   ```
   Replace `YOUR_API_KEY` with your actual key

**Important:** Without this API key, notes will only use relative positions (not world-locked).

## Firebase Storage Rules

Add these rules to Firebase Storage in the Firebase Console:

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /users/{userId}/{type}/{noteId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId;
      allow delete: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

This allows users to upload to:
- `users/{userId}/images/{noteId}.jpg`
- `users/{userId}/audio/{noteId}.3gp`

## Firestore Rules

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /notes/{noteId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == request.resource.data.userId;
      allow delete: if request.auth != null && request.auth.uid == resource.data.userId;
    }
  }
}
```