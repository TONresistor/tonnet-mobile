/**
 * Settings page - Mobile optimized.
 * Configure general, network, storage, and privacy settings.
 */

import { useState } from 'react'
import {
  Wifi,
  Palette,
  Trash2,
  Info,
  Shield,
  Home,
  Terminal,
  X,
  RefreshCw,
} from 'lucide-react'
import { platform } from '@/platform'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { usePreferencesStore, defaultPreferences } from '@/stores/preferences'
import { useProxyStore } from '@/stores/proxy'
import { useBookmarksStore } from '@/stores/bookmarks'
import { TonProxy } from '@/plugins/ton-proxy'
import { APP_NAME, APP_VERSION } from '@shared/constants'
import tonLogo from '@/assets/ton.png'

export function SettingsPage() {
  const [clearing, setClearing] = useState(false)
  const [cleared, setCleared] = useState(false)
  const [reconnecting, setReconnecting] = useState(false)
  const [showLogs, setShowLogs] = useState(false)
  const [logs, setLogs] = useState<string[]>([])
  const [loadingLogs, setLoadingLogs] = useState(false)

  const { draft } = usePreferencesStore()
  const { status, disconnect, connect } = useProxyStore()
  const { resetBookmarks } = useBookmarksStore()
  const isConnected = status === 'connected'

  const fetchLogs = async () => {
    setLoadingLogs(true)
    try {
      const result = await TonProxy.getLogs()
      setLogs(result.logs?.split('\n').filter(Boolean) || [])
    } catch (e) {
      console.error('Failed to fetch logs:', e)
    } finally {
      setLoadingLogs(false)
    }
  }

  const openLogs = () => {
    setShowLogs(true)
    fetchLogs()
  }

  // Use defaultPreferences as fallback during hydration
  const workingDraft = draft ?? defaultPreferences

  // Auto-save helper - updates preferences atomically to avoid race conditions
  const updateAndSave = <K extends keyof typeof workingDraft>(key: K, value: typeof workingDraft[K]) => {
    const store = usePreferencesStore.getState()
    const newPreferences = { ...store.preferences, [key]: value }

    // Apply theme change immediately if needed
    if (key === 'theme') {
      document.documentElement.setAttribute('data-theme', value as string)
    }

    usePreferencesStore.setState({
      preferences: newPreferences,
      draft: newPreferences,
      hasChanges: false,
    })
  }

  // Handle anonymous mode change - reconnect if currently connected
  const handleAnonymousModeChange = async (checked: boolean) => {
    updateAndSave('anonymousMode', checked)

    if (isConnected) {
      setReconnecting(true)
      try {
        await disconnect()
        // Small delay to ensure disconnect completes
        await new Promise(resolve => setTimeout(resolve, 500))
        await connect()
      } catch (err) {
        console.error('Failed to reconnect:', err)
      } finally {
        setReconnecting(false)
      }
    }
  }

  const handleClearData = async () => {
    setClearing(true)
    try {
      await platform.clearBrowsingData()
      resetBookmarks()
      setCleared(true)
      setTimeout(() => setCleared(false), 2000)
    } catch (err) {
      console.error('Failed to clear data:', err)
    } finally {
      setClearing(false)
    }
  }

  return (
    <div className="flex flex-col h-full bg-background-secondary overflow-auto">
      {/* Header */}
      <div className="bg-background border-b border-border p-4 pt-12 sticky top-0 z-10">
        <h1 className="text-lg font-semibold text-foreground">Settings</h1>
      </div>

      <div className="p-4 pb-24 space-y-6">
        {/* Network Section - with Anonymous Mode at top */}
        <SettingsSection title="Network" icon={Wifi}>
          {/* Anonymous Mode first */}
          <SettingsToggle
            label={reconnecting ? "Reconnecting..." : "Anonymous Mode"}
            description={reconnecting ? "Switching proxy mode..." : "Route through 3-hop circuit for privacy"}
            checked={workingDraft.anonymousMode}
            onChange={handleAnonymousModeChange}
            disabled={reconnecting}
          />

          {/* Conditional: Show when anonymous mode is enabled */}
          {workingDraft.anonymousMode && (
            <div className="pl-4 space-y-3 pt-2 border-t border-border">
              <SettingsToggle
                label="Circuit Rotation"
                description="Periodically change proxy circuit"
                checked={workingDraft.circuitRotation}
                onChange={(checked) => updateAndSave('circuitRotation', checked)}
              />

              {workingDraft.circuitRotation && (
                <SettingsRow
                  label="Rotation Interval"
                  description="How often to rotate circuit"
                >
                  <select
                    value={workingDraft.rotateInterval}
                    onChange={(e) => updateAndSave('rotateInterval', e.target.value)}
                    className="px-2 py-1 bg-background border border-border rounded text-foreground text-sm"
                  >
                    <option value="5m">5 min</option>
                    <option value="10m">10 min</option>
                    <option value="15m">15 min</option>
                    <option value="30m">30 min</option>
                  </select>
                </SettingsRow>
              )}
            </div>
          )}

          {/* Other network settings */}
          <div className="pt-2 border-t border-border space-y-3">
            <SettingsToggle
              label="Auto-connect"
              description="Connect to TON network on app launch"
              checked={workingDraft.autoConnect}
              onChange={(checked) => updateAndSave('autoConnect', checked)}
            />
            <SettingsRow
              label="Proxy Port"
              description="Port for the TON proxy server"
            >
              <input
                type="number"
                value={workingDraft.proxyPort}
                onChange={(e) => updateAndSave('proxyPort', parseInt(e.target.value) || 8080)}
                className="w-20 px-2 py-1 text-right bg-background border border-border rounded text-foreground text-sm"
              />
            </SettingsRow>
          </div>
        </SettingsSection>

        {/* General Section */}
        <SettingsSection title="General" icon={Home}>
          <SettingsRow
            label="Homepage"
            description="Page to open on new tab"
          >
            <select
              value={workingDraft.homepage}
              onChange={(e) => updateAndSave('homepage', e.target.value)}
              className="px-2 py-1 bg-background border border-border rounded text-foreground text-sm"
            >
              <option value="ton://start">Start Page</option>
            </select>
          </SettingsRow>
        </SettingsSection>

        {/* Appearance Section */}
        <SettingsSection title="Appearance" icon={Palette}>
          <div className="flex gap-2">
            <button
              onClick={() => updateAndSave('theme', 'resistance-dog')}
              className={cn(
                'flex-1 py-3 px-4 rounded-lg font-medium transition-all',
                workingDraft.theme === 'resistance-dog'
                  ? 'bg-[#5288c1] text-white'
                  : 'bg-background-secondary text-muted-foreground'
              )}
            >
              Resistance Dog
            </button>
            <button
              onClick={() => updateAndSave('theme', 'utya-duck')}
              className={cn(
                'flex-1 py-3 px-4 rounded-lg font-medium transition-all',
                workingDraft.theme === 'utya-duck'
                  ? 'bg-[#FFE600] text-[#141414]'
                  : 'bg-background-secondary text-muted-foreground'
              )}
            >
              Utya Duck
            </button>
          </div>
        </SettingsSection>

        {/* Privacy Section */}
        <SettingsSection title="Privacy" icon={Shield}>
          <SettingsToggle
            label="Clear on Exit"
            description="Clear browsing data when closing"
            checked={workingDraft.clearOnExit}
            onChange={(checked) => updateAndSave('clearOnExit', checked)}
          />
          <div className="pt-2">
            <Button
              onClick={handleClearData}
              disabled={clearing}
              variant="ghost"
              className="w-full justify-start text-destructive"
            >
              <Trash2 className="h-4 w-4 mr-2" />
              {cleared ? 'Cleared!' : clearing ? 'Clearing...' : 'Clear Browsing Data'}
            </Button>
          </div>
        </SettingsSection>

        {/* Debug Section */}
        <SettingsSection title="Debug" icon={Terminal}>
          <Button
            onClick={openLogs}
            variant="ghost"
            className="w-full justify-start"
          >
            <Terminal className="h-4 w-4 mr-2" />
            Proxy Logs
          </Button>
        </SettingsSection>

        {/* About Section */}
        <SettingsSection title="About" icon={Info}>
          <div className="flex flex-col items-center text-center py-4">
            <img src={tonLogo} alt="TON" className="w-16 h-16 mb-3" />
            <p className="font-semibold text-foreground text-lg">{APP_NAME}</p>
            <p className="text-sm text-muted-foreground">Version {APP_VERSION}</p>
            <p className="text-xs text-muted-foreground mt-3">
              A decentralized browser for the TON network.
            </p>
          </div>
        </SettingsSection>
      </div>

      {/* Log Modal */}
      {showLogs && (
        <div className="fixed inset-0 z-50 bg-black/90 flex flex-col">
          <div className="flex items-center justify-between p-3 pt-12 border-b border-border bg-background">
            <span className="font-medium">Proxy Logs</span>
            <div className="flex gap-1">
              <Button size="sm" variant="ghost" onClick={fetchLogs} disabled={loadingLogs}>
                <RefreshCw className={cn("h-4 w-4", loadingLogs && "animate-spin")} />
              </Button>
              <Button size="sm" variant="ghost" onClick={() => setShowLogs(false)}>
                <X className="h-4 w-4" />
              </Button>
            </div>
          </div>
          <div className="flex-1 overflow-auto p-2 font-mono text-xs">
            {logs.length === 0 ? (
              <p className="text-muted-foreground text-center py-8">
                {loadingLogs ? 'Loading...' : 'No logs. Connect to proxy first.'}
              </p>
            ) : (
              logs.map((line, i) => (
                <div key={i} className={cn(
                  "py-0.5 text-muted-foreground",
                  line.includes('[PROXY]') && "text-green-400",
                  line.includes('[tonnet-proxy]') && "text-purple-400",
                  line.includes('Error') && "text-red-400",
                )}>
                  {line}
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  )
}

// Settings Section Component
function SettingsSection({
  title,
  icon: Icon,
  children,
}: {
  title: string
  icon: React.ElementType
  children: React.ReactNode
}) {
  return (
    <div className="bg-background rounded-xl border border-border overflow-hidden">
      <div className="flex items-center gap-2 px-4 py-3 border-b border-border">
        <Icon className="h-4 w-4 text-primary" />
        <span className="font-medium text-foreground">{title}</span>
      </div>
      <div className="p-4 space-y-3">{children}</div>
    </div>
  )
}

// Settings Row Component
function SettingsRow({
  label,
  description,
  children,
}: {
  label: string
  description?: string
  children: React.ReactNode
}) {
  return (
    <div className="flex items-center justify-between">
      <div className="flex-1 mr-4">
        <p className="text-sm font-medium text-foreground">{label}</p>
        {description && (
          <p className="text-xs text-muted-foreground truncate">{description}</p>
        )}
      </div>
      {children}
    </div>
  )
}

// Settings Toggle Component
function SettingsToggle({
  label,
  description,
  checked,
  onChange,
  disabled = false,
}: {
  label: string
  description?: string
  checked: boolean
  onChange: (checked: boolean) => void
  disabled?: boolean
}) {
  return (
    <button
      onClick={() => !disabled && onChange(!checked)}
      disabled={disabled}
      className={cn(
        "w-full flex items-center justify-between py-1",
        disabled && "opacity-50 cursor-not-allowed"
      )}
    >
      <div className="flex-1 mr-4 text-left">
        <p className="text-sm font-medium text-foreground">{label}</p>
        {description && (
          <p className="text-xs text-muted-foreground">{description}</p>
        )}
      </div>
      <div
        className={cn(
          'w-11 h-6 rounded-full p-0.5 transition-colors',
          checked ? 'bg-primary' : 'bg-border'
        )}
      >
        <div
          className={cn(
            'w-5 h-5 rounded-full bg-white shadow transition-transform',
            checked ? 'translate-x-5' : 'translate-x-0'
          )}
        />
      </div>
    </button>
  )
}

