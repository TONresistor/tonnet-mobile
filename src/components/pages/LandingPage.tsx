/**
 * Landing page - initial connection screen.
 * Shows connect button and loading animation.
 */

import { useState, useEffect } from 'react'
import { useProxy } from '@/hooks/useProxy'
import { platform } from '@/platform'
import { useSettingsStore } from '@/stores/settings'
import welcomeGif from '@/assets/welcome.gif'
import welcomeYellowGif from '@/assets/welcome-yellow.gif'
import loadingGif from '@/assets/loading.gif'
import loadingYellowGif from '@/assets/loading-yellow.gif'
import { APP_VERSION } from '@shared/constants'
import { usePreferences } from '@/stores/preferences'

const CONNECTION_STEPS = [
  'Starting proxy...',
  'Syncing with network...',
  'Connected!'
]

export function LandingPage() {
  const { isConnecting, isConnected, error, connect } = useProxy()
  const { navigate } = useSettingsStore()
  const [currentStep, setCurrentStep] = useState(-1)
  const [stepMessage, setStepMessage] = useState('')
  const { theme, homepage } = usePreferences()
  const isYellow = theme === 'utya-duck'

  const currentWelcomeGif = isYellow ? welcomeYellowGif : welcomeGif
  const currentLoadingGif = isYellow ? loadingYellowGif : loadingGif

  // Navigate to homepage when connected
  useEffect(() => {
    if (isConnected) {
      navigate(homepage)
    }
  }, [isConnected, navigate, homepage])

  // Listen for proxy progress events
  useEffect(() => {
    const subscription = platform.on('proxy:progress', (data) => {
      console.log('[LandingPage] proxy:progress', data)
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

  const progressPercent = currentStep >= 0 ? ((currentStep + 1) / CONNECTION_STEPS.length) * 100 : 0

  return (
    <div className="relative flex flex-col items-center justify-center h-full w-full bg-background-secondary">
      {/* Logo - switches between welcome and loading gif */}
      <img
        src={isConnecting ? currentLoadingGif : currentWelcomeGif}
        alt="TON"
        className="w-[200px] h-[200px] mb-8 transition-opacity duration-300"
      />

      <h1 className="text-[42px] font-bold text-foreground mb-3">TON Browser</h1>

      <p className="text-muted-foreground text-xl mb-8">Explore the decentralized TON Network.</p>

      {/* Connect Button */}
      <button
        onClick={() => connect()}
        disabled={isConnecting}
        className={`
          relative text-primary-foreground text-xl font-medium px-16 py-5 rounded-xl min-w-[340px]
          transition-all duration-300 transform
          ${isConnecting
            ? 'gradient-primary opacity-80 cursor-not-allowed'
            : 'gradient-primary hover:-translate-y-0.5 hover:shadow-lg'
          }
          disabled:opacity-90
        `}
      >
        {isConnecting ? (
          <div className="flex items-center justify-center gap-3">
            <div className="w-6 h-6 border-2 border-primary-foreground/20 border-t-primary-foreground rounded-full animate-spin" />
            <span>{stepMessage || 'Connecting...'}</span>
          </div>
        ) : (
          'Connect to TON Network'
        )}
      </button>

      {/* Progress Section */}
      <div className={`mt-8 w-[340px] transition-opacity duration-300 ${isConnecting ? 'opacity-100' : 'opacity-0'}`}>
        {/* Progress Bar */}
        <div className="h-1.5 bg-foreground/10 rounded-full overflow-hidden mb-4">
          <div
            className="h-full gradient-primary transition-all duration-400 ease-out"
            style={{ width: `${progressPercent}%` }}
          />
        </div>

        {/* Step Label */}
        <p className={`text-center text-sm ${error ? 'text-destructive' : 'text-muted-foreground'}`}>
          {error || (currentStep >= 0 ? CONNECTION_STEPS[currentStep] : '')}
        </p>
      </div>

      {/* Footer */}
      <div className="absolute bottom-8 text-center">
        <p className="text-muted-foreground text-sm">Peer-to-peer - Censorship Resistant - No Tracking</p>
        <p className="text-muted-foreground/50 text-xs mt-1">v{APP_VERSION}</p>
      </div>
    </div>
  )
}
