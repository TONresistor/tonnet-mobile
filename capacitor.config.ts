import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.tonnet.browser',
  appName: 'Tonnet Browser',
  webDir: 'dist',
  server: {
    // Serve over HTTP to avoid mixed-content blocking in hardened WebViews
    // (e.g. Vanadium on GrapheneOS) which block HTTP iframes inside HTTPS pages
    // even with MIXED_CONTENT_ALWAYS_ALLOW. TON sites are loaded as HTTP iframes
    // through the local proxy, so the parent must also be HTTP.
    androidScheme: 'http'
  }
};

export default config;
