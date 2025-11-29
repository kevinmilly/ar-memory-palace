import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export interface ARSessionState {
  isActive: boolean;
  isInitialized: boolean;
  error: string | null;
  cameraPermission: 'granted' | 'denied' | 'prompt' | null;
}

@Injectable({
  providedIn: 'root'
})
export class ArSessionService {
  private sessionState = new BehaviorSubject<ARSessionState>({
    isActive: false,
    isInitialized: false,
    error: null,
    cameraPermission: null
  });

  public sessionState$ = this.sessionState.asObservable();

  constructor() {}

  /**
   * Get current session state
   */
  getSessionState(): ARSessionState {
    return this.sessionState.value;
  }

  /**
   * Get session state as observable
   */
  getSessionState$(): Observable<ARSessionState> {
    return this.sessionState$;
  }

  /**
   * Initialize AR session
   */
  initializeSession(): void {
    this.updateSessionState({
      isInitialized: true,
      error: null
    });
  }

  /**
   * Start AR session
   */
  startSession(): void {
    this.updateSessionState({
      isActive: true,
      error: null
    });
  }

  /**
   * Stop AR session
   */
  stopSession(): void {
    this.updateSessionState({
      isActive: false
    });
  }

  /**
   * Set session error
   */
  setError(error: string): void {
    this.updateSessionState({
      error,
      isActive: false
    });
  }

  /**
   * Set camera permission status
   */
  setCameraPermission(status: 'granted' | 'denied' | 'prompt'): void {
    this.updateSessionState({
      cameraPermission: status
    });
  }

  /**
   * Check if AR is supported
   */
  async checkARSupport(): Promise<boolean> {
    // Check for WebXR support
    if ('xr' in navigator) {
      try {
        const supported = await (navigator as any).xr.isSessionSupported('immersive-ar');
        return supported;
      } catch (error) {
        console.error('Error checking AR support:', error);
        return false;
      }
    }
    
    console.warn('WebXR not supported in this browser');
    return false;
  }

  /**
   * Request camera permission
   */
  async requestCameraPermission(): Promise<boolean> {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ 
        video: { facingMode: 'environment' } 
      });
      
      // Stop the stream immediately as we just needed permission
      stream.getTracks().forEach(track => track.stop());
      
      this.setCameraPermission('granted');
      return true;
    } catch (error) {
      console.error('Camera permission denied:', error);
      this.setCameraPermission('denied');
      return false;
    }
  }

  /**
   * Update session state
   */
  private updateSessionState(updates: Partial<ARSessionState>): void {
    this.sessionState.next({
      ...this.sessionState.value,
      ...updates
    });
  }

  /**
   * Reset session
   */
  reset(): void {
    this.sessionState.next({
      isActive: false,
      isInitialized: false,
      error: null,
      cameraPermission: null
    });
  }
}
