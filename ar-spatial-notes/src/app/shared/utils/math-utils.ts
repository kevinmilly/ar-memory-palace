import * as THREE from 'three';
import { AnchorPosition, AnchorRotation } from '../types/anchor.model';

export class MathUtils {
  /**
   * Convert Three.js Vector3 to AnchorPosition
   */
  static vector3ToPosition(vector: THREE.Vector3): AnchorPosition {
    return {
      x: vector.x,
      y: vector.y,
      z: vector.z
    };
  }

  /**
   * Convert AnchorPosition to Three.js Vector3
   */
  static positionToVector3(position: AnchorPosition): THREE.Vector3 {
    return new THREE.Vector3(position.x, position.y, position.z);
  }

  /**
   * Convert Three.js Euler to AnchorRotation
   */
  static eulerToRotation(euler: THREE.Euler): AnchorRotation {
    return {
      x: euler.x,
      y: euler.y,
      z: euler.z
    };
  }

  /**
   * Convert AnchorRotation to Three.js Euler
   */
  static rotationToEuler(rotation: AnchorRotation): THREE.Euler {
    return new THREE.Euler(rotation.x, rotation.y, rotation.z);
  }

  /**
   * Calculate distance between two positions
   */
  static distance(pos1: AnchorPosition, pos2: AnchorPosition): number {
    const dx = pos1.x - pos2.x;
    const dy = pos1.y - pos2.y;
    const dz = pos1.z - pos2.z;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  /**
   * Interpolate between two positions
   */
  static lerp(pos1: AnchorPosition, pos2: AnchorPosition, t: number): AnchorPosition {
    return {
      x: pos1.x + (pos2.x - pos1.x) * t,
      y: pos1.y + (pos2.y - pos1.y) * t,
      z: pos1.z + (pos2.z - pos1.z) * t
    };
  }
}
