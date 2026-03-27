/**
 * Start page - new tab homepage.
 * Simple search interface for .ton sites.
 */

import { useState, FormEvent } from 'react'
import explorerGif from '@/assets/explorer.gif'
import tonIcon from '@/assets/ton.png'
import { useIsMobile } from '@/hooks/useIsMobile'
import { useSettingsStore } from '@/stores/settings'
import { normalizeUrl } from '@/lib/url'

export function StartPage() {
  const [searchInput, setSearchInput] = useState('')
  const isMobile = useIsMobile()
  const { navigate } = useSettingsStore()

  const handleSearch = (e: FormEvent) => {
    e.preventDefault()
    const url = normalizeUrl(searchInput)
    if (url) {
      navigate(url)
    }
  }

  return (
    <div className="flex flex-col items-center justify-center h-full w-full bg-background-secondary">
      {/* Hero Section - centered */}
      <div className="flex flex-col items-center px-4">
        <img
          src={explorerGif}
          alt="TON"
          className={isMobile ? 'w-[140px] h-[140px] mb-6' : 'w-[200px] h-[200px] mb-8'}
        />

        <p className={`text-muted-foreground text-center mb-8 ${isMobile ? 'text-base px-4' : 'text-xl'}`}>
          Explore the decentralized TON Network
        </p>

        {/* Search Form */}
        <form onSubmit={handleSearch} className="w-full max-w-[500px] px-4">
          <div className={`flex items-center bg-background border border-border rounded-full focus-within:border-primary ${isMobile ? 'p-1' : 'p-1.5'}`}>
            <span className={isMobile ? 'px-3' : 'px-4'}>
              <img src={tonIcon} alt="TON" className={isMobile ? 'w-5 h-5' : 'w-6 h-6'} />
            </span>
            <input
              type="text"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              className={`flex-1 bg-transparent border-none text-foreground outline-none placeholder:text-muted-foreground/50 ${isMobile ? 'text-base py-3 pr-2' : 'text-lg py-4 pr-5'}`}
              placeholder="Enter a .ton address"
            />
            <button
              type="submit"
              className={`bg-primary text-primary-foreground rounded-full font-medium active:bg-accent transition-colors ${isMobile ? 'px-5 py-3 text-sm' : 'px-8 py-4 text-base'}`}
            >
              Go
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
