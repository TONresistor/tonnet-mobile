import { describe, expect, it } from 'vitest'
import { isLanguageCode, LANGUAGE_REGISTRY, SUPPORTED_LANGUAGES } from '../registry'

function getLeafPaths(value: unknown, prefix = ''): string[] {
  if (typeof value !== 'object' || value === null) return [prefix]

  return Object.entries(value).flatMap(([key, nestedValue]) =>
    getLeafPaths(nestedValue, prefix ? `${prefix}.${key}` : key),
  )
}

describe('language registry', () => {
  it('derives the supported language metadata from all 15 resources', () => {
    expect(SUPPORTED_LANGUAGES).toHaveLength(15)
    expect(SUPPORTED_LANGUAGES.map((language) => language.code)).toEqual(
      Object.keys(LANGUAGE_REGISTRY),
    )
    expect(SUPPORTED_LANGUAGES).toContainEqual({
      code: 'fr',
      label: 'French',
      nativeLabel: 'Français',
    })
  })

  it('keeps every namespace in parity with the English source', () => {
    const reference = LANGUAGE_REGISTRY.en.resources

    for (const [language, definition] of Object.entries(LANGUAGE_REGISTRY)) {
      for (const namespace of ['common', 'settings', 'landing', 'browser'] as const) {
        expect(
          getLeafPaths(definition.resources[namespace]).sort(),
          `${language}.${namespace}`,
        ).toEqual(getLeafPaths(reference[namespace]).sort())
      }
    }
  })

  it('accepts only own language registry keys', () => {
    expect(isLanguageCode('fr')).toBe(true)
    expect(isLanguageCode('constructor')).toBe(false)
    expect(isLanguageCode('toString')).toBe(false)
    expect(isLanguageCode(null)).toBe(false)
  })
})
