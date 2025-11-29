export interface AnchorPosition {
  x: number;
  y: number;
  z: number;
}

export interface AnchorRotation {
  x: number;
  y: number;
  z: number;
}

export interface Anchor {
  id?: string;
  userId: string;
  position: AnchorPosition;
  rotation: AnchorRotation;
  type: 'note' | 'image' | 'text';
  content: string;
  createdAt?: Date;
  updatedAt?: Date;
}

export interface FirestoreAnchor {
  userId: string;
  position: AnchorPosition;
  rotation: AnchorRotation;
  type: string;
  content: string;
  createdAt: any;
  updatedAt: any;
}
