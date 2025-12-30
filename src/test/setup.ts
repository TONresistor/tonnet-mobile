import '@testing-library/jest-dom'
import { beforeEach, vi } from 'vitest'

// Reset localStorage before each test
beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
})

// Mock console.log to avoid noise in tests
vi.spyOn(console, 'log').mockImplementation(() => {})
vi.spyOn(console, 'debug').mockImplementation(() => {})
