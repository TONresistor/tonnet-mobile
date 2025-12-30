/// <reference types="vite/client" />

/**
 * Vite environment types.
 * Provides TypeScript declarations for Vite-specific features.
 */

interface ImportMetaEnv {
  readonly VITE_APP_TITLE?: string
  readonly VITE_API_URL?: string
  readonly MODE: string
  readonly DEV: boolean
  readonly PROD: boolean
  readonly SSR: boolean
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
