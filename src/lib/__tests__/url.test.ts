/**
 * Tests for URL utilities.
 */

import { describe, it, expect } from 'vitest'
import { normalizeUrl } from '../url'

describe('normalizeUrl', () => {
  describe('empty input', () => {
    it('returns empty string for empty input', () => {
      expect(normalizeUrl('')).toBe('')
    })

    it('returns empty string for whitespace-only input', () => {
      expect(normalizeUrl('   ')).toBe('')
    })
  })

  describe('ton:// protocol', () => {
    it('passes through ton:// URLs unchanged', () => {
      expect(normalizeUrl('ton://start')).toBe('ton://start')
    })

    it('passes through ton:// with path unchanged', () => {
      expect(normalizeUrl('ton://settings')).toBe('ton://settings')
    })
  })

  describe('http(s):// protocol', () => {
    it('passes through http:// URLs unchanged', () => {
      expect(normalizeUrl('http://example.ton')).toBe('http://example.ton')
    })

    it('passes through https:// URLs unchanged', () => {
      expect(normalizeUrl('https://example.com')).toBe('https://example.com')
    })

    it('passes through http:// with path unchanged', () => {
      expect(normalizeUrl('http://site.ton/page')).toBe('http://site.ton/page')
    })
  })

  describe('auto-append .ton for domains without TLD', () => {
    it('appends .ton to single word domain', () => {
      expect(normalizeUrl('exemple')).toBe('http://exemple.ton')
    })

    it('appends .ton and preserves path', () => {
      expect(normalizeUrl('exemple/page')).toBe('http://exemple.ton/page')
    })

    it('appends .ton and preserves complex path', () => {
      expect(normalizeUrl('site/path/to/page')).toBe('http://site.ton/path/to/page')
    })

    it('trims whitespace before processing', () => {
      expect(normalizeUrl('  exemple  ')).toBe('http://exemple.ton')
    })
  })

  describe('domains with TLD', () => {
    it('prepends http:// to .ton domain', () => {
      expect(normalizeUrl('example.ton')).toBe('http://example.ton')
    })

    it('prepends http:// to .adnl domain', () => {
      expect(normalizeUrl('site.adnl')).toBe('http://site.adnl')
    })

    it('prepends http:// to .com domain', () => {
      expect(normalizeUrl('google.com')).toBe('http://google.com')
    })

    it('prepends http:// and preserves path', () => {
      expect(normalizeUrl('site.ton/page/test')).toBe('http://site.ton/page/test')
    })
  })

  describe('edge cases', () => {
    it('handles subdomain correctly (has dot = has TLD)', () => {
      expect(normalizeUrl('sub.example.ton')).toBe('http://sub.example.ton')
    })

    it('handles domain with port', () => {
      // Port contains no dot, but domain does
      expect(normalizeUrl('example.ton:8080')).toBe('http://example.ton:8080')
    })
  })
})
