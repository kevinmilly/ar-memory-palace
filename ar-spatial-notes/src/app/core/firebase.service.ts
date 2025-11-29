import { Injectable } from '@angular/core';
import { initializeApp, FirebaseApp } from 'firebase/app';
import { 
  getFirestore, 
  Firestore, 
  collection, 
  addDoc, 
  getDocs, 
  doc, 
  deleteDoc, 
  query, 
  where,
  Timestamp 
} from 'firebase/firestore';
import { getAuth, Auth, signInAnonymously, User } from 'firebase/auth';
import { getStorage, FirebaseStorage } from 'firebase/storage';
import { environment } from '../../environments/environment';
import { Anchor, FirestoreAnchor } from '../shared/types/anchor.model';

@Injectable({
  providedIn: 'root'
})
export class FirebaseService {
  private app: FirebaseApp | null = null;
  private firestore: Firestore | null = null;
  private auth: Auth | null = null;
  private storage: FirebaseStorage | null = null;
  private currentUser: User | null = null;

  constructor() {
    this.initFirebase();
  }

  /**
   * Initialize Firebase services
   */
  initFirebase(): void {
    try {
      // Check if Firebase config is provided
      if (!environment.firebase.apiKey) {
        console.warn('Firebase configuration not found. Please add your Firebase config to environment.ts');
        return;
      }

      this.app = initializeApp(environment.firebase);
      this.firestore = getFirestore(this.app);
      this.auth = getAuth(this.app);
      this.storage = getStorage(this.app);

      // Sign in anonymously
      this.signInAnonymous();
    } catch (error) {
      console.error('Error initializing Firebase:', error);
    }
  }

  /**
   * Sign in user anonymously
   */
  async signInAnonymous(): Promise<User | null> {
    if (!this.auth) {
      console.error('Firebase Auth not initialized');
      return null;
    }

    try {
      const result = await signInAnonymously(this.auth);
      this.currentUser = result.user;
      console.log('Signed in anonymously:', this.currentUser.uid);
      return this.currentUser;
    } catch (error) {
      console.error('Error signing in anonymously:', error);
      return null;
    }
  }

  /**
   * Get Firestore instance
   */
  getFirestore(): Firestore | null {
    return this.firestore;
  }

  /**
   * Get Storage instance
   */
  getStorage(): FirebaseStorage | null {
    return this.storage;
  }

  /**
   * Get Auth instance
   */
  getAuth(): Auth | null {
    return this.auth;
  }

  /**
   * Get current user ID
   */
  getCurrentUserId(): string | null {
    return this.currentUser?.uid || null;
  }

  /**
   * Save an anchor to Firestore
   */
  async saveAnchor(anchor: Anchor): Promise<string | null> {
    if (!this.firestore) {
      console.error('Firestore not initialized');
      return null;
    }

    if (!this.currentUser) {
      console.error('User not authenticated');
      return null;
    }

    try {
      const anchorData: FirestoreAnchor = {
        userId: anchor.userId || this.currentUser.uid,
        position: anchor.position,
        rotation: anchor.rotation,
        type: anchor.type,
        content: anchor.content,
        createdAt: Timestamp.now(),
        updatedAt: Timestamp.now()
      };

      const docRef = await addDoc(collection(this.firestore, 'anchors'), anchorData);
      console.log('Anchor saved with ID:', docRef.id);
      return docRef.id;
    } catch (error) {
      console.error('Error saving anchor:', error);
      return null;
    }
  }

  /**
   * Get all anchors for a user
   */
  async getAnchors(userId?: string): Promise<Anchor[]> {
    if (!this.firestore) {
      console.error('Firestore not initialized');
      return [];
    }

    const targetUserId = userId || this.currentUser?.uid;
    if (!targetUserId) {
      console.error('No user ID provided');
      return [];
    }

    try {
      const anchorsRef = collection(this.firestore, 'anchors');
      const q = query(anchorsRef, where('userId', '==', targetUserId));
      const querySnapshot = await getDocs(q);

      const anchors: Anchor[] = [];
      querySnapshot.forEach((doc) => {
        const data = doc.data() as FirestoreAnchor;
        anchors.push({
          id: doc.id,
          userId: data.userId,
          position: data.position,
          rotation: data.rotation,
          type: data.type as 'note' | 'image' | 'text',
          content: data.content,
          createdAt: data.createdAt?.toDate(),
          updatedAt: data.updatedAt?.toDate()
        });
      });

      console.log(`Retrieved ${anchors.length} anchors for user ${targetUserId}`);
      return anchors;
    } catch (error) {
      console.error('Error getting anchors:', error);
      return [];
    }
  }

  /**
   * Delete an anchor
   */
  async deleteAnchor(anchorId: string): Promise<boolean> {
    if (!this.firestore) {
      console.error('Firestore not initialized');
      return false;
    }

    try {
      await deleteDoc(doc(this.firestore, 'anchors', anchorId));
      console.log('Anchor deleted:', anchorId);
      return true;
    } catch (error) {
      console.error('Error deleting anchor:', error);
      return false;
    }
  }
}
