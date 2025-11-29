import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import WebXRPolyfill from 'webxr-polyfill';

// Initialize WebXR polyfill for better cross-browser support
const polyfill = new WebXRPolyfill();
console.log('WebXR Polyfill initialized');

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
