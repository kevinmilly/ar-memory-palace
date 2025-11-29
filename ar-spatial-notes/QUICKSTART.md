# 🎯 Quick Reference Card

## 🚦 Start Here

1. **Configure Firebase** (5 minutes)
   - Go to: https://console.firebase.google.com
   - Create project → Enable Firestore + Auth (Anonymous)
   - Copy config to: `src/environments/environment.ts`

2. **Configure 8th Wall** (3 minutes)
   - Go to: https://console.8thwall.com
   - Create Web project → Copy App Key
   - Add to: `src/index.html` and `src/environments/environment.ts`

3. **Run the app**
   ```bash
   cd ar-spatial-notes
   npm start
   ```

## 📋 Essential Commands

```bash
# Development
npm start              # Start dev server (port 4200)
npm run dev           # Start and open browser
npm run watch         # Watch mode for development

# Build
npm run build         # Build for development
npm run build:prod    # Build for production

# Test
npm test              # Run tests
```

## 📱 Test on Mobile (Codespaces)

1. `npm start`
2. VS Code → Ports tab → Port 4200 → Set to "Public"
3. Copy HTTPS URL
4. Open on phone → Allow camera → Tap "Start AR"

## 🔥 Firebase Quick Setup

### 1. Create Project
```
Firebase Console → Add Project → Follow wizard
```

### 2. Enable Services
```
Firestore Database → Create database → Test mode
Authentication → Get Started → Enable Anonymous
Storage → Get Started (optional)
```

### 3. Get Config
```
Project Settings → Your apps → Web app → Config object
```

### 4. Add to environment.ts
```typescript
firebase: {
  apiKey: "...",
  authDomain: "...",
  projectId: "...",
  storageBucket: "...",
  messagingSenderId: "...",
  appId: "..."
}
```

### 5. Security Rules
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

## 🌐 8th Wall Quick Setup

### 1. Create Account
```
https://www.8thwall.com → Sign Up (free trial available)
```

### 2. Create Project
```
Console → New Project → Web
```

### 3. Get App Key
```
Project → Settings → Copy App Key
```

### 4. Add to index.html
```html
<!-- In <head> section -->
<script async src="//cdn.8thwall.com/web/xrextras/xrextras.js"></script>
<script async src="//apps.8thwall.com/xrweb?appKey=YOUR_KEY"></script>
```

### 5. Add Allowed Domains
```
Project → Settings → Allowed Domains
Add: localhost, your-codespace-url, your-vercel-domain
```

## 🚀 Deploy to Vercel

```bash
# One-time setup
npm install -g vercel
vercel login

# Deploy
cd ar-spatial-notes
npm run build:prod
vercel --prod

# Follow prompts:
# - Project name: ar-spatial-notes
# - Override settings: No
```

## 📂 Key Files to Edit

| File | Purpose |
|------|---------|
| `src/environments/environment.ts` | Firebase & 8th Wall config (dev) |
| `src/environments/environment.prod.ts` | Firebase & 8th Wall config (prod) |
| `src/index.html` | 8th Wall script tags |
| `src/app/ar/ar-view/ar-view.component.ts` | AR logic & Three.js |
| `src/app/core/firebase.service.ts` | Firebase operations |
| `vercel.json` | Vercel deployment config |

## 🔍 Debugging

### Check if Firebase is working:
```
Browser Console → Look for:
"Signed in anonymously: [user-id]"
```

### Check if AR is supported:
```
Browser Console → Look for:
"AR session started"
```

### Common Issues:

**Camera not working?**
- Must use HTTPS (not localhost)
- Check browser permissions
- Try Chrome on Android / Safari on iOS

**AR not starting?**
- Verify 8th Wall key in index.html
- Check 8th Wall allowed domains
- Ensure device supports WebXR

**Anchors not saving?**
- Check Firebase config is correct
- Verify Firestore rules are set
- Check browser console for errors

## 📊 Project Stats

- **Lines of Code**: ~1,500
- **Services**: 3 (Firebase, Anchor Storage, AR Session)
- **Components**: 1 (AR View)
- **Models**: 1 (Anchor)
- **Build Size**: ~228 KB (initial) + 851 KB (AR lazy loaded)

## 🎓 Learning Resources

- **Angular**: https://angular.dev/docs
- **Three.js**: https://threejs.org/docs
- **Firebase**: https://firebase.google.com/docs
- **8th Wall**: https://www.8thwall.com/docs
- **WebXR**: https://immersiveweb.dev

## 💡 Pro Tips

1. **Always test on HTTPS** - Camera won't work on HTTP
2. **Use Codespaces** - Built-in HTTPS forwarding
3. **Check Console** - Errors are logged there
4. **Test on Real Device** - AR needs actual hardware
5. **Start Simple** - Get one anchor working first

## 🆘 Get Help

1. Check `SETUP.md` for detailed instructions
2. Check `README.md` for full documentation
3. Check `PROJECT_SUMMARY.md` for overview
4. Browser console for runtime errors
5. Firebase/8th Wall console for service issues

---

**Quick Start Checklist:**
- [ ] Firebase config added to environment.ts
- [ ] 8th Wall key added to index.html
- [ ] Firestore rules configured
- [ ] npm start running
- [ ] Port 4200 set to Public
- [ ] Tested on mobile with HTTPS
- [ ] Camera permission granted
- [ ] AR session started
- [ ] Anchor placed and saved

**Ready? Run `npm start` and build something amazing! 🚀**
