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

**For 365-day Cloud Anchor TTL (instead of 24-hour limit):**

The API key method limits you to 24-hour TTL. To get 365-day persistence:

1. **Enable OAuth 2.0 for Cloud Anchors**:
   - In Google Cloud Console → APIs & Services → Credentials
   - Find your Android app's OAuth 2.0 Client ID
   - Make sure it has these scopes enabled:
     - `https://www.googleapis.com/auth/cloud-platform`
     - `https://www.googleapis.com/auth/arcore-anchor`

2. **In Firebase Console** → Authentication → Sign-in method:
   - Ensure Google Sign-In is enabled
   - The OAuth client ID should match your Android app

3. **The app will automatically use OAuth** when users sign in with Google, enabling 365-day TTL

**Current setup:** Uses Firebase Auth token which supports 365-day TTL when properly configured.

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