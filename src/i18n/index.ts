import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import enCommon from './locales/en/common.json'
import enSettings from './locales/en/settings.json'
import enLanding from './locales/en/landing.json'
import enBrowser from './locales/en/browser.json'

import ruCommon from './locales/ru/common.json'
import ruSettings from './locales/ru/settings.json'
import ruLanding from './locales/ru/landing.json'
import ruBrowser from './locales/ru/browser.json'

import zhCommon from './locales/zh/common.json'
import zhSettings from './locales/zh/settings.json'
import zhLanding from './locales/zh/landing.json'
import zhBrowser from './locales/zh/browser.json'

import esCommon from './locales/es/common.json'
import esSettings from './locales/es/settings.json'
import esLanding from './locales/es/landing.json'
import esBrowser from './locales/es/browser.json'

import itCommon from './locales/it/common.json'
import itSettings from './locales/it/settings.json'
import itLanding from './locales/it/landing.json'
import itBrowser from './locales/it/browser.json'

import jaCommon from './locales/ja/common.json'
import jaSettings from './locales/ja/settings.json'
import jaLanding from './locales/ja/landing.json'
import jaBrowser from './locales/ja/browser.json'

import koCommon from './locales/ko/common.json'
import koSettings from './locales/ko/settings.json'
import koLanding from './locales/ko/landing.json'
import koBrowser from './locales/ko/browser.json'

import trCommon from './locales/tr/common.json'
import trSettings from './locales/tr/settings.json'
import trLanding from './locales/tr/landing.json'
import trBrowser from './locales/tr/browser.json'

import viCommon from './locales/vi/common.json'
import viSettings from './locales/vi/settings.json'
import viLanding from './locales/vi/landing.json'
import viBrowser from './locales/vi/browser.json'

import hiCommon from './locales/hi/common.json'
import hiSettings from './locales/hi/settings.json'
import hiLanding from './locales/hi/landing.json'
import hiBrowser from './locales/hi/browser.json'

import ptCommon from './locales/pt/common.json'
import ptSettings from './locales/pt/settings.json'
import ptLanding from './locales/pt/landing.json'
import ptBrowser from './locales/pt/browser.json'

import ukCommon from './locales/uk/common.json'
import ukSettings from './locales/uk/settings.json'
import ukLanding from './locales/uk/landing.json'
import ukBrowser from './locales/uk/browser.json'

import myCommon from './locales/my/common.json'
import mySettings from './locales/my/settings.json'
import myLanding from './locales/my/landing.json'
import myBrowser from './locales/my/browser.json'

import frCommon from './locales/fr/common.json'
import frSettings from './locales/fr/settings.json'
import frLanding from './locales/fr/landing.json'
import frBrowser from './locales/fr/browser.json'

import deCommon from './locales/de/common.json'
import deSettings from './locales/de/settings.json'
import deLanding from './locales/de/landing.json'
import deBrowser from './locales/de/browser.json'

export const SUPPORTED_LANGUAGES = [
  { code: 'en', label: 'English', nativeLabel: 'English' },
  { code: 'fr', label: 'French', nativeLabel: 'Français' },
  { code: 'de', label: 'German', nativeLabel: 'Deutsch' },
  { code: 'ru', label: 'Russian', nativeLabel: 'Русский' },
  { code: 'zh', label: 'Chinese', nativeLabel: '中文' },
  { code: 'es', label: 'Spanish', nativeLabel: 'Español' },
  { code: 'it', label: 'Italian', nativeLabel: 'Italiano' },
  { code: 'ja', label: 'Japanese', nativeLabel: '日本語' },
  { code: 'ko', label: 'Korean', nativeLabel: '한국어' },
  { code: 'tr', label: 'Turkish', nativeLabel: 'Türkçe' },
  { code: 'vi', label: 'Vietnamese', nativeLabel: 'Tiếng Việt' },
  { code: 'hi', label: 'Hindi', nativeLabel: 'हिन्दी' },
  { code: 'pt', label: 'Portuguese', nativeLabel: 'Português' },
  { code: 'uk', label: 'Ukrainian', nativeLabel: 'Українська' },
  { code: 'my', label: 'Burmese', nativeLabel: 'မြန်မာ' },
] as const

export type LanguageCode = typeof SUPPORTED_LANGUAGES[number]['code']

i18n.use(initReactI18next).init({
  resources: {
    en: {
      common: enCommon,
      settings: enSettings,
      landing: enLanding,
      browser: enBrowser,
    },
    ru: {
      common: ruCommon,
      settings: ruSettings,
      landing: ruLanding,
      browser: ruBrowser,
    },
    zh: {
      common: zhCommon,
      settings: zhSettings,
      landing: zhLanding,
      browser: zhBrowser,
    },
    es: {
      common: esCommon,
      settings: esSettings,
      landing: esLanding,
      browser: esBrowser,
    },
    it: {
      common: itCommon,
      settings: itSettings,
      landing: itLanding,
      browser: itBrowser,
    },
    ja: {
      common: jaCommon,
      settings: jaSettings,
      landing: jaLanding,
      browser: jaBrowser,
    },
    ko: {
      common: koCommon,
      settings: koSettings,
      landing: koLanding,
      browser: koBrowser,
    },
    tr: {
      common: trCommon,
      settings: trSettings,
      landing: trLanding,
      browser: trBrowser,
    },
    vi: {
      common: viCommon,
      settings: viSettings,
      landing: viLanding,
      browser: viBrowser,
    },
    hi: {
      common: hiCommon,
      settings: hiSettings,
      landing: hiLanding,
      browser: hiBrowser,
    },
    pt: {
      common: ptCommon,
      settings: ptSettings,
      landing: ptLanding,
      browser: ptBrowser,
    },
    uk: {
      common: ukCommon,
      settings: ukSettings,
      landing: ukLanding,
      browser: ukBrowser,
    },
    my: {
      common: myCommon,
      settings: mySettings,
      landing: myLanding,
      browser: myBrowser,
    },
    fr: {
      common: frCommon,
      settings: frSettings,
      landing: frLanding,
      browser: frBrowser,
    },
    de: {
      common: deCommon,
      settings: deSettings,
      landing: deLanding,
      browser: deBrowser,
    },
  },
  lng: 'en',
  fallbackLng: 'en',
  defaultNS: 'common',
  interpolation: {
    escapeValue: false,
  },
})

// Sync language from preferences store
import { usePreferencesStore } from '@/stores/preferences'

const initialLang = usePreferencesStore.getState().preferences.language
if (initialLang) {
  i18n.changeLanguage(initialLang)
}

usePreferencesStore.subscribe((state) => {
  const lang = state.preferences.language
  if (lang && lang !== i18n.language) {
    i18n.changeLanguage(lang)
  }
})

export default i18n
