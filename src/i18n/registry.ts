import deBrowser from './locales/de/browser.json'
import deCommon from './locales/de/common.json'
import deLanding from './locales/de/landing.json'
import deSettings from './locales/de/settings.json'
import enBrowser from './locales/en/browser.json'
import enCommon from './locales/en/common.json'
import enLanding from './locales/en/landing.json'
import enSettings from './locales/en/settings.json'
import esBrowser from './locales/es/browser.json'
import esCommon from './locales/es/common.json'
import esLanding from './locales/es/landing.json'
import esSettings from './locales/es/settings.json'
import frBrowser from './locales/fr/browser.json'
import frCommon from './locales/fr/common.json'
import frLanding from './locales/fr/landing.json'
import frSettings from './locales/fr/settings.json'
import hiBrowser from './locales/hi/browser.json'
import hiCommon from './locales/hi/common.json'
import hiLanding from './locales/hi/landing.json'
import hiSettings from './locales/hi/settings.json'
import itBrowser from './locales/it/browser.json'
import itCommon from './locales/it/common.json'
import itLanding from './locales/it/landing.json'
import itSettings from './locales/it/settings.json'
import jaBrowser from './locales/ja/browser.json'
import jaCommon from './locales/ja/common.json'
import jaLanding from './locales/ja/landing.json'
import jaSettings from './locales/ja/settings.json'
import koBrowser from './locales/ko/browser.json'
import koCommon from './locales/ko/common.json'
import koLanding from './locales/ko/landing.json'
import koSettings from './locales/ko/settings.json'
import myBrowser from './locales/my/browser.json'
import myCommon from './locales/my/common.json'
import myLanding from './locales/my/landing.json'
import mySettings from './locales/my/settings.json'
import ptBrowser from './locales/pt/browser.json'
import ptCommon from './locales/pt/common.json'
import ptLanding from './locales/pt/landing.json'
import ptSettings from './locales/pt/settings.json'
import ruBrowser from './locales/ru/browser.json'
import ruCommon from './locales/ru/common.json'
import ruLanding from './locales/ru/landing.json'
import ruSettings from './locales/ru/settings.json'
import trBrowser from './locales/tr/browser.json'
import trCommon from './locales/tr/common.json'
import trLanding from './locales/tr/landing.json'
import trSettings from './locales/tr/settings.json'
import ukBrowser from './locales/uk/browser.json'
import ukCommon from './locales/uk/common.json'
import ukLanding from './locales/uk/landing.json'
import ukSettings from './locales/uk/settings.json'
import viBrowser from './locales/vi/browser.json'
import viCommon from './locales/vi/common.json'
import viLanding from './locales/vi/landing.json'
import viSettings from './locales/vi/settings.json'
import zhBrowser from './locales/zh/browser.json'
import zhCommon from './locales/zh/common.json'
import zhLanding from './locales/zh/landing.json'
import zhSettings from './locales/zh/settings.json'

interface LanguageDefinition {
  label: string
  nativeLabel: string
  resources: {
    common: Record<string, unknown>
    settings: Record<string, unknown>
    landing: Record<string, unknown>
    browser: Record<string, unknown>
  }
}

export const LANGUAGE_REGISTRY = {
  en: {
    label: 'English',
    nativeLabel: 'English',
    resources: { common: enCommon, settings: enSettings, landing: enLanding, browser: enBrowser },
  },
  fr: {
    label: 'French',
    nativeLabel: 'Français',
    resources: { common: frCommon, settings: frSettings, landing: frLanding, browser: frBrowser },
  },
  de: {
    label: 'German',
    nativeLabel: 'Deutsch',
    resources: { common: deCommon, settings: deSettings, landing: deLanding, browser: deBrowser },
  },
  ru: {
    label: 'Russian',
    nativeLabel: 'Русский',
    resources: { common: ruCommon, settings: ruSettings, landing: ruLanding, browser: ruBrowser },
  },
  zh: {
    label: 'Chinese',
    nativeLabel: '中文',
    resources: { common: zhCommon, settings: zhSettings, landing: zhLanding, browser: zhBrowser },
  },
  es: {
    label: 'Spanish',
    nativeLabel: 'Español',
    resources: { common: esCommon, settings: esSettings, landing: esLanding, browser: esBrowser },
  },
  it: {
    label: 'Italian',
    nativeLabel: 'Italiano',
    resources: { common: itCommon, settings: itSettings, landing: itLanding, browser: itBrowser },
  },
  ja: {
    label: 'Japanese',
    nativeLabel: '日本語',
    resources: { common: jaCommon, settings: jaSettings, landing: jaLanding, browser: jaBrowser },
  },
  ko: {
    label: 'Korean',
    nativeLabel: '한국어',
    resources: { common: koCommon, settings: koSettings, landing: koLanding, browser: koBrowser },
  },
  tr: {
    label: 'Turkish',
    nativeLabel: 'Türkçe',
    resources: { common: trCommon, settings: trSettings, landing: trLanding, browser: trBrowser },
  },
  vi: {
    label: 'Vietnamese',
    nativeLabel: 'Tiếng Việt',
    resources: { common: viCommon, settings: viSettings, landing: viLanding, browser: viBrowser },
  },
  hi: {
    label: 'Hindi',
    nativeLabel: 'हिन्दी',
    resources: { common: hiCommon, settings: hiSettings, landing: hiLanding, browser: hiBrowser },
  },
  pt: {
    label: 'Portuguese',
    nativeLabel: 'Português',
    resources: { common: ptCommon, settings: ptSettings, landing: ptLanding, browser: ptBrowser },
  },
  uk: {
    label: 'Ukrainian',
    nativeLabel: 'Українська',
    resources: { common: ukCommon, settings: ukSettings, landing: ukLanding, browser: ukBrowser },
  },
  my: {
    label: 'Burmese',
    nativeLabel: 'မြန်မာ',
    resources: { common: myCommon, settings: mySettings, landing: myLanding, browser: myBrowser },
  },
} as const satisfies Record<string, LanguageDefinition>

export type LanguageCode = keyof typeof LANGUAGE_REGISTRY

const languageCodes = Object.keys(LANGUAGE_REGISTRY) as LanguageCode[]
const languageCodeSet = new Set<string>(languageCodes)

export const SUPPORTED_LANGUAGES = languageCodes.map((code) => ({
  code,
  label: LANGUAGE_REGISTRY[code].label,
  nativeLabel: LANGUAGE_REGISTRY[code].nativeLabel,
}))

export const I18N_RESOURCES = Object.fromEntries(
  languageCodes.map((code) => [code, LANGUAGE_REGISTRY[code].resources]),
) as unknown as Record<LanguageCode, LanguageDefinition['resources']>

export function isLanguageCode(value: unknown): value is LanguageCode {
  return typeof value === 'string' && languageCodeSet.has(value)
}
