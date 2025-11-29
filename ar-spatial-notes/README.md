# 🌐 AR Spatial Notes

A cross-platform WebAR application for placing and persisting spatial notes in augmented reality using Angular, Three.js, 8th Wall, and Firebase.

![AR Spatial Notes](https://img.shields.io/badge/Angular-21-red?logo=angular)
![Three.js](https://img.shields.io/badge/Three.js-0.181-black?logo=three.js)
![Firebase](https://img.shields.io/badge/Firebase-12-orange?logo=firebase)
![TypeScript](https://img.shields.io/badge/TypeScript-5.9-blue?logo=typescript)

## 🎯 Features

- ✨ **WebAR** - Browser-based AR experience using WebXR and 8th Wall
- 📍 **Spatial Anchors** - Place sticky notes in 3D space
- 💾 **Cloud Persistence** - Store and retrieve anchors via Firebase Firestore
- 🔒 **Anonymous Auth** - Get started instantly without sign-up
- 📱 **Cross-Platform** - Works on iOS and Android devices
- 🎨 **Three.js Rendering** - Beautiful 3D graphics and interactions

## 📋 Prerequisites

- Node.js 20 or higher
- npm or yarn
- Firebase project (for backend)
- 8th Wall account (for WebAR features)
- HTTPS hosting (required for camera access)

## 🚀 Quick Start

### 1. Clone and Install

```bash
cd ar-spatial-notes
npm install
```

### 2. Configure Firebase

Create a Firebase project at [firebase.google.com](https://firebase.google.com)

Enable these services:
- **Firestore Database** - For storing AR anchors
- **Authentication** - Enable Anonymous auth
- **Storage** - For image uploads (optional)

Update `src/environments/environment.ts`:

```typescript
export const environment = {
  production: false,
  firebase: {
    apiKey: "YOUR_API_KEY",
    authDomain: "YOUR_PROJECT.firebaseapp.com",
    projectId: "YOUR_PROJECT_ID",
    storageBucket: "YOUR_PROJECT.appspot.com",
    messagingSenderId: "YOUR_SENDER_ID",
    appId: "YOUR_APP_ID"
  },
  eighthWall: {
    appKey: "YOUR_8TH_WALL_APP_KEY"
  }
};
```

### 3. Configure 8th Wall

1. Create an account at [8thwall.com](https://www.8thwall.com)
2. Create a new Web AR project
3. Copy your App Key
4. Update `src/index.html` with your 8th Wall scripts:

```html
<script async src="//cdn.8thwall.com/web/xrextras/xrextras.js"></script>
<script async src="//apps.8thwall.com/xrweb?appKey=YOUR_APP_KEY_HERE"></script>
```

### 4. Run Development Server

```bash
npm start
```

The app will be available at `http://localhost:4200`

**Note:** For AR features to work, you need HTTPS. Use the Codespaces preview URL or deploy to a hosting service.

## 📱 Testing on Mobile

### Using GitHub Codespaces:

1. Start the dev server: `npm start`
2. Click the "Ports" tab in VS Code
3. Find port 4200 and change visibility to "Public"
4. Copy the forwarded HTTPS URL
5. Open the URL on your mobile device
6. Grant camera permissions when prompted
7. Tap "Start AR" to begin

### Using ngrok (alternative):

```bash
npm install -g ngrok
npm start
# In another terminal:
ngrok http 4200
```

Open the ngrok HTTPS URL on your phone.

## 🏗️ Project Structure

```
ar-spatial-notes/
├── src/
│   ├── app/
│   │   ├── core/                    # Core services
│   │   │   ├── firebase.service.ts  # Firebase integration
│   │   │   ├── anchor-storage.service.ts  # Anchor CRUD
│   │   │   └── ar-session.service.ts      # AR session management
│   │   ├── ar/
│   │   │   └── ar-view/             # Main AR component
│   │   │       ├── ar-view.component.ts
│   │   │       ├── ar-view.component.html
│   │   │       └── ar-view.component.scss
│   │   ├── shared/
│   │   │   ├── types/
│   │   │   │   └── anchor.model.ts  # TypeScript interfaces
│   │   │   └── utils/
│   │   │       └── math-utils.ts    # 3D math utilities
│   │   ├── app.routes.ts
│   │   └── app.config.ts
│   ├── environments/
│   │   ├── environment.ts           # Dev config
│   │   └── environment.prod.ts      # Prod config
│   └── index.html
├── .devcontainer/
│   └── devcontainer.json            # Codespaces config
└── package.json
```

## 🔧 Development

### Available Scripts

```bash
# Start dev server (accessible from network)
npm start

# Start dev server and open browser
npm run dev

# Build for production
npm run build:prod

# Run tests
npm test

# Watch mode for development
npm run watch
```

## 🎮 How to Use

1. **Start AR Session** - Tap "Start AR" button
2. **Grant Camera Access** - Allow camera permissions
3. **Find a Surface** - Point camera at a flat surface
4. **Place Note** - Tap on the reticle to place a sticky note
5. **Notes Persist** - Your notes are saved to Firebase and will reload on next session

## 🔥 Firestore Schema

### Collection: `anchors`

```typescript
{
  userId: string;           // Anonymous user ID
  position: {
    x: number;
    y: number;
    z: number;
  };
  rotation: {
    x: number;
    y: number;
    z: number;
  };
  type: "note" | "image" | "text";
  content: string;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}
```

## 🌐 Deployment

### Deploy to Vercel

1. Install Vercel CLI:
```bash
npm install -g vercel
```

2. Build the project:
```bash
npm run build:prod
```

3. Deploy:
```bash
vercel --prod
```

4. Configure Vercel:
   - Build Command: `npm run build:prod`
   - Output Directory: `dist/ar-spatial-notes/browser`
   - Install Command: `npm install`

### Deploy to Firebase Hosting

1. Install Firebase CLI:
```bash
npm install -g firebase-tools
```

2. Initialize Firebase Hosting:
```bash
firebase init hosting
```

3. Build and deploy:
```bash
npm run build:prod
firebase deploy --only hosting
```

### Environment Variables for Production

Update `src/environments/environment.prod.ts` with your production Firebase config.

## 🐛 Troubleshooting

### Camera Not Working
- Ensure you're using HTTPS (required for camera access)
- Check browser permissions for camera access
- Try a different browser (Chrome/Safari recommended)

### AR Not Starting
- Verify 8th Wall App Key is correctly configured
- Check browser console for errors
- Ensure device supports WebXR

### Anchors Not Saving
- Verify Firebase config is correct
- Check Firestore rules allow anonymous auth
- Ensure internet connection is stable

### Codespaces Preview Issues
- Make sure port 4200 is set to "Public" visibility
- Check if firewall/network blocks the forwarded URL
- Try accessing from a different network

## 📚 Tech Stack Details

- **Angular 21** - Modern web framework with standalone components
- **Three.js 0.181** - 3D graphics library
- **Firebase 12** - Backend as a service
  - Firestore - NoSQL database
  - Auth - Anonymous authentication
  - Storage - File uploads
- **8th Wall** - WebAR platform
- **TypeScript 5.9** - Type-safe development
- **SCSS** - Styling
- **RxJS** - Reactive programming

## 🔒 Security

### Firestore Security Rules

Add these rules to your Firestore:

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

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Open a Pull Request

## 📄 License

MIT License - feel free to use this project for learning or commercial purposes.

## 🙏 Acknowledgments

- [8th Wall](https://www.8thwall.com) for WebAR capabilities
- [Three.js](https://threejs.org) for 3D rendering
- [Firebase](https://firebase.google.com) for backend services
- [Angular](https://angular.dev) for the framework

## 📞 Support

For issues and questions:
- Open an issue on GitHub
- Check the [8th Wall documentation](https://www.8thwall.com/docs)
- Review [Three.js examples](https://threejs.org/examples/)
- Read [Angular documentation](https://angular.dev/docs)

---

**Built with ❤️ for the AR web**

Happy spatial computing! 🚀
