# AR Memory Palace

Android AR app for creating spatial notes with text, images, and audio.

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