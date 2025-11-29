import { Component, OnInit, OnDestroy, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import * as THREE from 'three';
import { AnchorStorageService } from '../../core/anchor-storage.service';
import { ArSessionService } from '../../core/ar-session.service';
import { Anchor } from '../../shared/types/anchor.model';

@Component({
  selector: 'app-ar-view',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ar-view.component.html',
  styleUrls: ['./ar-view.component.scss']
})
export class ArViewComponent implements OnInit, OnDestroy {
  @ViewChild('arCanvas', { static: true }) canvasRef!: ElementRef<HTMLCanvasElement>;

  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private renderer!: THREE.WebGLRenderer;
  private reticle!: THREE.Mesh;
  private hitTestSource: any = null;
  private hitTestSourceRequested = false;
  private placedAnchors: THREE.Mesh[] = [];
  private animationFrameId: number | null = null;
  private xrSession: any = null;

  isARSupported = false;
  isARActive = false;
  errorMessage: string | null = null;

  constructor(
    private anchorStorage: AnchorStorageService,
    private arSession: ArSessionService
  ) {}

  async ngOnInit(): Promise<void> {
    // Debug: Check WebXR availability
    if (!('xr' in navigator)) {
      alert('WebXR not supported or insecure context (Codespaces iframe issue)');
      console.error('WebXR not in navigator');
    }
    
    // Check AR support
    console.log('Checking AR support...');
    console.log('Navigator.xr available:', 'xr' in navigator);
    
    this.isARSupported = await this.arSession.checkARSupport();
    
    console.log('AR Supported:', this.isARSupported);
    
    if (!this.isARSupported) {
      console.warn('AR not supported. Showing 3D preview mode.');
      console.warn('User Agent:', navigator.userAgent);
      this.errorMessage = 'AR not supported on this device. Showing 3D preview.';
    }

    // Initialize Three.js scene
    this.initThreeJS();
    
    // Load existing anchors
    await this.loadAnchors();

    // Start render loop
    this.animate();

    // Subscribe to session state
    this.arSession.getSessionState$().subscribe(state => {
      this.isARActive = state.isActive;
      if (state.error) {
        this.errorMessage = state.error;
      }
    });
  }

  ngOnDestroy(): void {
    if (this.animationFrameId) {
      cancelAnimationFrame(this.animationFrameId);
    }
    
    if (this.xrSession) {
      this.xrSession.end();
    }

    if (this.renderer) {
      this.renderer.dispose();
    }
  }

  /**
   * Initialize Three.js scene, camera, and renderer
   */
  private initThreeJS(): void {
    const canvas = this.canvasRef.nativeElement;

    // Scene
    this.scene = new THREE.Scene();

    // Camera
    this.camera = new THREE.PerspectiveCamera(
      70,
      window.innerWidth / window.innerHeight,
      0.01,
      20
    );

    // Renderer
    this.renderer = new THREE.WebGLRenderer({
      canvas,
      alpha: true,
      antialias: true
    });
    this.renderer.setSize(window.innerWidth, window.innerHeight);
    this.renderer.setPixelRatio(window.devicePixelRatio);
    this.renderer.xr.enabled = true;

    // Lighting
    const light = new THREE.HemisphereLight(0xffffff, 0xbbbbff, 1);
    light.position.set(0.5, 1, 0.25);
    this.scene.add(light);

    // Reticle (target indicator for placement)
    const reticleGeometry = new THREE.RingGeometry(0.15, 0.2, 32).rotateX(-Math.PI / 2);
    const reticleMaterial = new THREE.MeshBasicMaterial({ color: 0xffffff });
    this.reticle = new THREE.Mesh(reticleGeometry, reticleMaterial);
    this.reticle.matrixAutoUpdate = false;
    this.reticle.visible = false;
    this.scene.add(this.reticle);

    // Handle window resize
    window.addEventListener('resize', () => this.onWindowResize());

    // TODO: 8th Wall XR initialization would go here
    // This is a placeholder for 8th Wall integration
    console.log('TODO: Initialize 8th Wall XR runtime');
  }

  /**
   * Start AR session
   */
  async startAR(): Promise<void> {
    if (!this.isARSupported) {
      this.errorMessage = 'AR not supported on this device';
      return;
    }

    try {
      console.log('Starting AR session...');
      
      // Request camera permission
      console.log('Requesting camera permission...');
      const hasPermission = await this.arSession.requestCameraPermission();
      if (!hasPermission) {
        console.error('Camera permission denied');
        this.errorMessage = 'Camera permission denied';
        return;
      }
      console.log('Camera permission granted');

      // Request AR session
      const xr = (navigator as any).xr;
      if (!xr) {
        console.error('WebXR not available on navigator');
        this.errorMessage = 'WebXR not available';
        return;
      }

      console.log('Requesting immersive-ar session...');
      this.xrSession = await xr.requestSession('immersive-ar', {
        requiredFeatures: ['hit-test', 'local-floor'],
        optionalFeatures: ['dom-overlay'],
        domOverlay: { root: document.body }
      });

      await this.renderer.xr.setSession(this.xrSession);

      // Setup hit test
      this.xrSession.requestReferenceSpace('viewer').then((refSpace: any) => {
        this.xrSession.requestHitTestSource({ space: refSpace }).then((source: any) => {
          this.hitTestSource = source;
        });
      });

      // Handle session end
      this.xrSession.addEventListener('end', () => {
        this.onARSessionEnd();
      });

      // Handle select (tap) events
      this.xrSession.addEventListener('select', (event: any) => {
        this.onSelect(event);
      });

      this.arSession.startSession();
      console.log('AR session started');
    } catch (error: any) {
      console.error('Error starting AR session:', error);
      console.error('Error name:', error?.name);
      console.error('Error message:', error?.message);
      this.errorMessage = `Failed to start AR: ${error?.message || 'Unknown error'}`;
      this.arSession.setError(`Failed to start AR session: ${error?.message || 'Unknown error'}`);
    }
  }

  /**
   * Handle AR session end
   */
  private onARSessionEnd(): void {
    this.hitTestSource = null;
    this.xrSession = null;
    this.reticle.visible = false;
    this.arSession.stopSession();
    console.log('AR session ended');
  }

  /**
   * Handle select (tap) event to place anchor
   */
  private onSelect(event: any): void {
    if (!this.reticle.visible) return;

    // Get reticle position and rotation
    const position = new THREE.Vector3();
    const rotation = new THREE.Euler();
    
    this.reticle.matrix.decompose(
      position,
      new THREE.Quaternion(),
      new THREE.Vector3()
    );

    // Create and save anchor
    const anchor = this.anchorStorage.createAnchorFromHitTest(
      position,
      rotation,
      'note',
      'Sample note'
    );

    this.anchorStorage.saveAnchor(anchor).then(anchorId => {
      if (anchorId) {
        anchor.id = anchorId;
        const mesh = this.anchorStorage.createThreeObjectFromAnchor(anchor);
        this.scene.add(mesh);
        this.placedAnchors.push(mesh);
        console.log('Anchor placed:', anchorId);
      }
    });
  }

  /**
   * Load existing anchors from Firestore
   */
  private async loadAnchors(): Promise<void> {
    try {
      const anchors = await this.anchorStorage.getAnchors();
      console.log(`Loading ${anchors.length} anchors`);

      anchors.forEach(anchor => {
        const mesh = this.anchorStorage.createThreeObjectFromAnchor(anchor);
        this.scene.add(mesh);
        this.placedAnchors.push(mesh);
      });

      // TODO: On session start, load Firestore anchors and place them into the scene
      // with proper relocalization based on stored world coordinates
    } catch (error) {
      console.error('Error loading anchors:', error);
    }
  }

  /**
   * Animation loop
   */
  private animate(): void {
    this.animationFrameId = requestAnimationFrame(() => this.animate());

    if (this.xrSession && this.hitTestSource) {
      const frame = this.renderer.xr.getFrame();
      if (frame) {
        const referenceSpace = this.renderer.xr.getReferenceSpace();
        if (referenceSpace) {
          const hitTestResults = frame.getHitTestResults(this.hitTestSource);

          if (hitTestResults.length > 0) {
            const hit = hitTestResults[0];
            const pose = hit.getPose(referenceSpace);

            if (pose) {
              this.reticle.visible = true;
              this.reticle.matrix.fromArray(pose.transform.matrix);
            }
          } else {
            this.reticle.visible = false;
          }
        }
      }
    }

    this.renderer.render(this.scene, this.camera);
  }

  /**
   * Handle window resize
   */
  private onWindowResize(): void {
    this.camera.aspect = window.innerWidth / window.innerHeight;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(window.innerWidth, window.innerHeight);
  }

  /**
   * Clear all placed anchors
   */
  async clearAllAnchors(): Promise<void> {
    for (const mesh of this.placedAnchors) {
      const anchorId = mesh.userData['anchorId'];
      if (anchorId) {
        await this.anchorStorage.deleteAnchor(anchorId);
      }
      this.scene.remove(mesh);
    }
    this.placedAnchors = [];
    console.log('All anchors cleared');
  }
}
