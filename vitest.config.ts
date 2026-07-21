import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: './src/test/setup.ts',
      include: ['src/**/*.test.{ts,tsx}'],
      coverage: {
        provider: 'v8',
        reporter: ['text', 'json-summary', 'html'],
        include: [
          'src/stores/**/*.{ts,tsx}',
          'src/lib/**/*.{ts,tsx}',
          'src/platform/**/*.{ts,tsx}',
          'src/hooks/**/*.{ts,tsx}',
        ],
        exclude: [
          'src/**/*.d.ts',
          'src/**/*.test.{ts,tsx}',
          'src/**/__tests__/**',
          'src/test/**',
          'src/**/types.ts',
        ],
        thresholds: {
          perFile: true,
          branches: 75,
          functions: 80,
          lines: 80,
          statements: 80,
        },
      },
    },
  }),
)
