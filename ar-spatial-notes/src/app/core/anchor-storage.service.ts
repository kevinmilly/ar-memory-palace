import { Injectable } from '@angular/core';
import { FirebaseService } from './firebase.service';
import { Anchor } from '../shared/types/anchor.model';
import * as THREE from 'three';
import { MathUtils } from '../shared/utils/math-utils';

@Injectable({
  providedIn: 'root'
})
export class AnchorStorageService {
  private anchors: Map<string, Anchor> = new Map();

  constructor(private firebaseService: FirebaseService) {}

  /**
   * Save an anchor to Firestore and local cache
   */
  async saveAnchor(anchor: Anchor): Promise<string | null> {
    const anchorId = await this.firebaseService.saveAnchor(anchor);
    
    if (anchorId) {
      anchor.id = anchorId;
      this.anchors.set(anchorId, anchor);
    }
    
    return anchorId;
  }

  /**
   * Get all anchors for the current user
   */
  async getAnchors(userId?: string): Promise<Anchor[]> {
    const anchors = await this.firebaseService.getAnchors(userId);
    
    // Update local cache
    this.anchors.clear();
    anchors.forEach(anchor => {
      if (anchor.id) {
        this.anchors.set(anchor.id, anchor);
      }
    });
    
    return anchors;
  }

  /**
   * Delete an anchor
   */
  async deleteAnchor(anchorId: string): Promise<boolean> {
    const success = await this.firebaseService.deleteAnchor(anchorId);
    
    if (success) {
      this.anchors.delete(anchorId);
    }
    
    return success;
  }

  /**
   * Get anchor by ID from local cache
   */
  getAnchorById(anchorId: string): Anchor | undefined {
    return this.anchors.get(anchorId);
  }

  /**
   * Get all anchors from local cache
   */
  getAllCachedAnchors(): Anchor[] {
    return Array.from(this.anchors.values());
  }

  /**
   * Convert anchor position to Three.js Object3D
   */
  createThreeObjectFromAnchor(anchor: Anchor): THREE.Mesh {
    // Create a simple sticky note plane
    const geometry = new THREE.PlaneGeometry(0.2, 0.2);
    const material = new THREE.MeshBasicMaterial({
      color: 0xffeb3b,
      side: THREE.DoubleSide
    });
    
    const mesh = new THREE.Mesh(geometry, material);
    
    // Set position and rotation
    const position = MathUtils.positionToVector3(anchor.position);
    const rotation = MathUtils.rotationToEuler(anchor.rotation);
    
    mesh.position.copy(position);
    mesh.rotation.copy(rotation);
    
    // Store anchor ID in user data
    mesh.userData = { anchorId: anchor.id, anchor };
    
    return mesh;
  }

  /**
   * Create an anchor from Three.js hit test result
   */
  createAnchorFromHitTest(
    position: THREE.Vector3,
    rotation: THREE.Euler,
    type: 'note' | 'image' | 'text' = 'note',
    content: string = 'Sample note'
  ): Anchor {
    const userId = this.firebaseService.getCurrentUserId() || 'anonymous';
    
    return {
      userId,
      position: MathUtils.vector3ToPosition(position),
      rotation: MathUtils.eulerToRotation(rotation),
      type,
      content,
      createdAt: new Date()
    };
  }

  /**
   * Clear local cache
   */
  clearCache(): void {
    this.anchors.clear();
  }
}
