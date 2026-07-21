import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import { DEFAULT_LANGUAGE } from '@/shared/constants'
import { usePreferencesStore } from '@/stores/preferences'
import { I18N_RESOURCES } from './registry'

export { SUPPORTED_LANGUAGES } from './registry'

void i18n.use(initReactI18next).init({
  resources: I18N_RESOURCES,
  lng: DEFAULT_LANGUAGE,
  fallbackLng: DEFAULT_LANGUAGE,
  defaultNS: 'common',
  interpolation: {
    escapeValue: false,
  },
})

const initialLanguage = usePreferencesStore.getState().preferences.language
void i18n.changeLanguage(initialLanguage)

usePreferencesStore.subscribe((state) => {
  const language = state.preferences.language
  if (language !== i18n.language) void i18n.changeLanguage(language)
})
