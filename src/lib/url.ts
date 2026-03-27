/**
 * URL utilities for TON browser.
 */

/**
 * Normalize URL input:
 * - Handle ton:// protocol
 * - Auto-append .ton if no TLD detected
 * - Prepend http:// if no protocol
 */
export function normalizeUrl(input: string): string {
  const trimmed = input.trim()
  if (!trimmed) return ''

  // Block dangerous schemes
  const lower = trimmed.toLowerCase()
  if (lower.startsWith('javascript:') || lower.startsWith('data:') || lower.startsWith('vbscript:')) {
    return ''
  }

  // Handle ton:// protocol - pass through
  if (trimmed.startsWith('ton://')) {
    return trimmed
  }

  // Handle http(s):// protocol - pass through
  if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
    return trimmed
  }

  // No protocol - analyze domain
  const [domainPart, ...pathParts] = trimmed.split('/')
  const path = pathParts.length > 0 ? '/' + pathParts.join('/') : ''

  // Check if domain has a TLD (contains a dot)
  if (!domainPart.includes('.')) {
    // No TLD - append .ton
    return `http://${domainPart}.ton${path}`
  }

  // Has TLD - just prepend http://
  return `http://${trimmed}`
}
