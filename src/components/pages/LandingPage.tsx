/**
 * Landing page - initial connection screen.
 * Shows connect button and loading animation.
 */

import { Loader2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import loadingGif from '@/assets/loading.gif'
import welcomeGif from '@/assets/welcome.gif'
import { useProxy } from '@/hooks/useProxy'
import { createLogger } from '@/lib/logger'
import { normalizeUrl } from '@/lib/url'
import { platform } from '@/platform'
import { APP_VERSION, INTERNAL_ROUTES } from '@/shared/constants'
import { useNavigationStore } from '@/stores/navigation'
import { usePreferences } from '@/stores/preferences'

const logger = createLogger('LandingPage')

export function LandingPage() {
  const { t } = useTranslation('landing')
  const connectionSteps = [t('step_starting'), t('step_syncing'), t('step_connected')]
  const { isConnecting, isConnected, error, connect } = useProxy()
  const navigate = useNavigationStore((state) => state.navigate)
  const [currentStep, setCurrentStep] = useState(-1)
  const [stepMessage, setStepMessage] = useState('')
  const { homepage } = usePreferences()
  // A cleared navigation session can remount this page while the native proxy is
  // still connected. In that case, continue directly to the configured homepage.
  useEffect(() => {
    if (isConnected) {
      const destination = normalizeUrl(homepage)
      navigate(
        !destination || destination === INTERNAL_ROUTES.landing
          ? INTERNAL_ROUTES.start
          : destination,
      )
    }
  }, [isConnected, navigate, homepage])

  // Listen for proxy progress events
  useEffect(() => {
    const subscription = platform.on('proxy:progress', (data) => {
      setCurrentStep(data.step)
      setStepMessage(data.message)
    })

    return () => {
      subscription.remove()
    }
  }, [])

  // Reset step when not connecting
  useEffect(() => {
    if (!isConnecting) {
      setCurrentStep(-1)
      setStepMessage('')
    }
  }, [isConnecting])

  const progressPercent = currentStep >= 0 ? ((currentStep + 1) / connectionSteps.length) * 100 : 0

  return (
    <div className="relative flex flex-col items-center justify-center h-full w-full bg-background-secondary">
      {/* Logo - switches between welcome and loading gif */}
      <img
        src={isConnecting ? loadingGif : welcomeGif}
        alt="TON"
        className="w-[140px] h-[140px] mb-6 transition-opacity duration-300"
      />

      <h1 className="text-2xl font-bold text-foreground mb-2">{t('title')}</h1>

      <p className="text-muted-foreground text-base mb-8 px-4 text-center">{t('subtitle')}</p>

      {/* Connect Button */}
      <button
        type="button"
        onClick={() => {
          void connect().catch((connectionError) => {
            logger.warn('Manual proxy connection failed', connectionError)
          })
        }}
        disabled={isConnecting}
        className={`
          relative text-primary-foreground text-base font-medium px-10 py-4 rounded-full min-w-[280px]
          transition-all duration-300 transform
          ${
            isConnecting
              ? 'gradient-primary opacity-80 cursor-not-allowed'
              : 'gradient-primary hover:-translate-y-0.5 hover:shadow-lg'
          }
          disabled:opacity-90
        `}
      >
        {isConnecting ? (
          <div className="flex items-center justify-center gap-2">
            <Loader2 className="w-5 h-5 text-primary-foreground animate-spin" />
            <span>{stepMessage || t('connecting')}</span>
          </div>
        ) : (
          t('connect_button')
        )}
      </button>

      {/* Progress Section */}
      <div
        className={`mt-6 w-[280px] transition-opacity duration-300 ${isConnecting || error ? 'opacity-100' : 'opacity-0'}`}
      >
        {/* Progress Bar */}
        <div className="h-1.5 bg-foreground/10 rounded-full overflow-hidden mb-4">
          <div
            className="h-full gradient-primary transition-all duration-300 ease-out"
            style={{ width: `${progressPercent}%` }}
          />
        </div>

        {/* Step Label */}
        <p
          className={`text-center text-sm ${error ? 'text-destructive' : 'text-muted-foreground'}`}
        >
          {error || (currentStep >= 0 ? connectionSteps[currentStep] : '')}
        </p>
      </div>

      {/* Footer */}
      <div className="absolute bottom-8 text-center">
        <p className="text-muted-foreground text-sm">{t('footer')}</p>
        <p className="text-muted-foreground/50 text-xs mt-1">{APP_VERSION}</p>
      </div>
    </div>
  )
}
