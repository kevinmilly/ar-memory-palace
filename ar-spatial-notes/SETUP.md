# AR Spatial Notes - Setup Guide

## 📋 Quick Setup Checklist

### 1. ✅ Firebase Setup

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project (or select existing)
3. Enable these services:

#### Firestore Database
- Go to "Firestore Database"
- Click "Create database"
- Choose "Start in test mode" (for development)
- Select a location

#### Authentication
- Go to "Authentication"
- Click "Get Started"
- Enable "Anonymous" sign-in method

#### Storage (Optional)
- Go to "Storage"
- Click "Get Started"
- Use default security rules

4. Get your Firebase config:
   - Go to Project Settings (gear icon)
   - Scroll to "Your apps"
   - Click "Web" app (or create one)
   - Copy the config object

5. Update `src/environments/environment.ts`:

```typescript
export const environment = {
  production: false,
  firebase: {
    apiKey: "AIza...",
    authDomain: "your-app.firebaseapp.com",
    projectId: "your-app",
    storageBucket: "your-app.appspot.com",
    messagingSenderId: "123456789",
    appId: "1:123:web:abc123"
  },
  eighthWall: {
    appKey: ""
  }
};
```

### 2. ✅ 8th Wall Setup

1. Go to [8th Wall Console](https://console.8thwall.com/)
2. Create account (free trial available)
3. Create a new "Web" project
4. Copy your App Key

5. Update `src/index.html`:

```html
<!-- Replace the TODO comment with: -->
<script async src="//cdn.8thwall.com/web/xrextras/xrextras.js"></script>
<script async src="//apps.8thwall.com/xrweb?appKey=YOUR_APP_KEY_HERE"></script>
```

6. Update `src/environments/environment.ts`:

```typescript
eighthWall: {
  appKey: "YOUR_8TH_WALL_APP_KEY"
}
```

### 3. ✅ Test Locally

```bash
cd ar-spatial-notes
npm install
npm start
```

Open http://localhost:4200 in your browser.

**Note:** WebAR features require HTTPS and won't work on localhost. Use Codespaces or deploy to test AR.

### 4. ✅ Test on Mobile (Codespaces)

1. Make sure dev server is running: `npm start`
2. In VS Code, open the "Ports" panel
3. Find port 4200
4. Right-click → Change Port Visibility → "Public"
5. Copy the forwarded URL (it will be HTTPS)
6. Open the URL on your phone
7. Allow camera permissions
8. Tap "Start AR"

### 5. ✅ Deploy to Vercel

```bash
# Install Vercel CLI
npm install -g vercel

# Login
vercel login

# Deploy
cd ar-spatial-notes
npm run build:prod
vercel --prod
```

Follow the prompts:
- Set up and deploy? Yes
- Which scope? (Select your account)
- Link to existing project? No
- Project name? ar-spatial-notes
- Directory? ./
- Override settings? No

Your app will be deployed to: `https://ar-spatial-notes.vercel.app`

### 6. ✅ Configure Production Environment

Update `src/environments/environment.prod.ts` with your production Firebase config.

Rebuild and redeploy:
```bash
npm run build:prod
vercel --prod
```

### 7. ✅ Update 8th Wall Allowed Domains

1. Go to 8th Wall Console
2. Open your project
3. Go to "Settings" → "Web Configuration"
4. Add your Vercel domain to "Allowed Domains":
   - `ar-spatial-notes.vercel.app`
   - `*.vercel.app` (for preview deployments)

### 8. ✅ Update Firebase Security Rules

In Firebase Console → Firestore → Rules:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /anchors/{anchorId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null 
        && request.auth.uid == request.resource.data.userId;
      allow delete: if request.auth != null 
        && request.auth.uid == resource.data.userId;
    }
  }
}
```

Click "Publish"

## 🎉 You're Done!

Test your deployed app:
1. Open the Vercel URL on your phone
2. Tap "Start AR"
3. Grant camera permissions
4. Find a flat surface
5. Tap to place a sticky note
6. Refresh the page - your note should persist!

## 🐛 Common Issues

### "Camera permission denied"
- Make sure you're using HTTPS
- Check browser settings → Site permissions
- Try a different browser

### "AR not supported"
- Use a recent iOS (Safari) or Android (Chrome) device
- Ensure device has ARCore/ARKit support
- Update browser to latest version

### "Firebase not initialized"
- Check console for errors
- Verify Firebase config is correct
- Make sure Firebase project has correct services enabled

### Anchors not persisting
- Check Firestore rules
- Verify network connection
- Open browser console to see errors
- Check Firebase Authentication is working

## 📚 Next Steps

- Customize the sticky note appearance
- Add text input for notes
- Implement image upload
- Add user accounts
- Create shared spaces
- Add spatial audio

Happy building! 🚀
