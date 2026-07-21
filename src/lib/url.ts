const EXPLICIT_SCHEME = /^([a-z][a-z\d+.-]*):/i
const HOST_WITH_PORT = /^[^/?#:\s]+:\d+(?:[/?#]|$)/
const BLOCKED_SCHEMES = new Set(['data', 'file', 'javascript', 'vbscript'])
const TON_HOST_SUFFIXES = ['ton', 'adnl', 't.me'] as const

function splitAuthority(input: string): { authority: string; suffix: string } {
  const suffixIndex = input.search(/[/?#]/)
  if (suffixIndex < 0) return { authority: input, suffix: '' }

  return {
    authority: input.slice(0, suffixIndex),
    suffix: input.slice(suffixIndex),
  }
}

function appendTonTld(authority: string): string {
  const portMatch = authority.match(/:(\d+)$/)
  const port = portMatch?.[0] ?? ''
  const hostname = port ? authority.slice(0, -port.length) : authority

  return hostname.includes('.') || hostname.startsWith('[') ? authority : `${hostname}.ton${port}`
}

/** Normalize a user-entered address and reject executable or unsupported protocols. */
export function normalizeUrl(input: string): string {
  const trimmed = input.trim()
  if (!trimmed) return ''

  const schemeMatch = trimmed.match(EXPLICIT_SCHEME)
  if (schemeMatch && BLOCKED_SCHEMES.has(schemeMatch[1].toLowerCase())) return ''

  if (schemeMatch && !HOST_WITH_PORT.test(trimmed)) {
    const scheme = schemeMatch[1].toLowerCase()
    if (!['http', 'https', 'ton'].includes(scheme)) return ''
    if (!trimmed.toLowerCase().startsWith(`${scheme}://`)) return ''
    return trimmed
  }

  const { authority, suffix } = splitAuthority(trimmed)
  if (!authority) return ''

  return `http://${appendTonTld(authority)}${suffix}`
}

export function formatDisplayUrl(url: string): string {
  return url.replace(/^https?:\/\//i, '')
}

export function isTonSiteUrl(url: string): boolean {
  if (!url) return false

  const scheme = url.match(EXPLICIT_SCHEME)?.[1]?.toLowerCase()
  if (scheme && scheme !== 'http' && scheme !== 'https') return false

  try {
    const parsedUrl = new URL(/^https?:\/\//i.test(url) ? url : `http://${url}`)
    const hostname = parsedUrl.hostname.toLowerCase()

    return TON_HOST_SUFFIXES.some(
      (suffix) => hostname === suffix || hostname.endsWith(`.${suffix}`),
    )
  } catch {
    return false
  }
}
