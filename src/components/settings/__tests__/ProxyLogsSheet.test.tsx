import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import '@/i18n'
import { ProxyLogsSheet } from '../ProxyLogsSheet'

describe('ProxyLogsSheet', () => {
  it('is unmounted while closed', () => {
    render(
      <ProxyLogsSheet
        open={false}
        logs={[]}
        loading={false}
        onClose={vi.fn()}
        onRefresh={vi.fn()}
      />,
    )

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('has an accessible name, initial focus and shared Escape behavior', () => {
    const onClose = vi.fn()
    render(
      <ProxyLogsSheet
        open
        logs={['[PROXY] ready']}
        loading={false}
        onClose={onClose}
        onRefresh={vi.fn()}
      />,
    )

    expect(screen.getByRole('dialog', { name: 'Proxy Logs' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Refresh' })).toHaveFocus()

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
