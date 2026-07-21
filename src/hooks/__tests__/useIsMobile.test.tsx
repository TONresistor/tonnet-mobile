import { act, renderHook } from '@testing-library/react'
import { renderToString } from 'react-dom/server'
import { afterEach, describe, expect, it } from 'vitest'
import { useIsMobile } from '../useIsMobile'

const originalWidth = window.innerWidth

function resizeTo(width: number): void {
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: width })
  window.dispatchEvent(new Event('resize'))
}

function IsMobileValue() {
  return useIsMobile() ? 'mobile' : 'desktop'
}

describe('useIsMobile', () => {
  afterEach(() => {
    resizeTo(originalWidth)
  })

  it('tracks the mobile breakpoint as the viewport changes', () => {
    resizeTo(480)
    const { result } = renderHook(() => useIsMobile())
    expect(result.current).toBe(true)

    act(() => resizeTo(1_024))
    expect(result.current).toBe(false)
  })

  it('uses a desktop-safe default during server rendering', () => {
    const browserWindow = window
    Object.defineProperty(globalThis, 'window', { configurable: true, value: undefined })

    try {
      expect(renderToString(<IsMobileValue />)).toContain('desktop')
    } finally {
      Object.defineProperty(globalThis, 'window', { configurable: true, value: browserWindow })
    }
  })
})
