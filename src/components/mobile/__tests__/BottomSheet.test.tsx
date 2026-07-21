import { fireEvent, render, screen, within } from '@testing-library/react'
import { useState } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import '@/i18n'
import { BottomSheet } from '../BottomSheet'

function ControlledSheet({ title = 'Actions' }: { title?: string }) {
  const [open, setOpen] = useState(false)

  return (
    <>
      <button type="button" onClick={() => setOpen(true)}>
        Open
      </button>
      <BottomSheet open={open} onClose={() => setOpen(false)} title={title}>
        <button type="button">First action</button>
        <button type="button">Last action</button>
      </BottomSheet>
    </>
  )
}

describe('BottomSheet', () => {
  beforeEach(() => {
    document.body.style.overflow = ''
  })

  it('stays unmounted while closed and restores focus after closing', () => {
    render(<ControlledSheet />)
    const trigger = screen.getByRole('button', { name: 'Open' })

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    trigger.focus()
    fireEvent.click(trigger)

    const dialog = screen.getByRole('dialog', { name: 'Actions' })
    expect(dialog).toBeInTheDocument()
    expect(document.body.style.overflow).toBe('hidden')
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toHaveFocus()
    expect(trigger.closest('[inert]')).not.toBeNull()

    trigger.focus()
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toHaveFocus()

    fireEvent.keyDown(document, { key: 'Escape' })

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(document.body.style.overflow).toBe('')
    expect(trigger).toHaveFocus()
  })

  it('keeps keyboard focus inside the active dialog', () => {
    render(<ControlledSheet />)
    fireEvent.click(screen.getByRole('button', { name: 'Open' }))

    const dialog = screen.getByRole('dialog', { name: 'Actions' })
    const first = within(dialog).getByRole('button', { name: 'Cancel' })
    const last = within(dialog).getByRole('button', { name: 'Last action' })

    last.focus()
    fireEvent.keyDown(last, { key: 'Tab' })
    expect(first).toHaveFocus()

    fireEvent.keyDown(first, { key: 'Tab', shiftKey: true })
    expect(last).toHaveFocus()
  })

  it('closes stacked dialogs in LIFO order with Escape', () => {
    function StackedSheets() {
      const [firstOpen, setFirstOpen] = useState(true)
      const [secondOpen, setSecondOpen] = useState(true)

      return (
        <>
          <BottomSheet open={firstOpen} onClose={() => setFirstOpen(false)} title="First">
            First content
          </BottomSheet>
          <BottomSheet open={secondOpen} onClose={() => setSecondOpen(false)} title="Second">
            Second content
          </BottomSheet>
        </>
      )
    }

    render(<StackedSheets />)

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('dialog', { name: 'Second' })).not.toBeInTheDocument()
    const firstDialog = screen.getByRole('dialog', { name: 'First' })
    expect(firstDialog).toBeInTheDocument()
    expect(firstDialog.closest('[data-modal-root]')).not.toHaveAttribute('inert')
    expect(document.body.style.overflow).toBe('hidden')

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(document.body.style.overflow).toBe('')
  })

  it('resets touch state on every gesture and on cancellation', () => {
    const onClose = vi.fn()
    render(
      <BottomSheet open onClose={onClose} title="Gestures">
        Content
      </BottomSheet>,
    )
    const dialog = screen.getByRole('dialog', { name: 'Gestures' })

    fireEvent.touchStart(dialog, { touches: [{ clientY: 100 }] })
    fireEvent.touchMove(dialog, { touches: [{ clientY: 250 }] })
    expect(dialog.style.transform).toBe('translateY(150px)')
    fireEvent.touchCancel(dialog)
    expect(dialog.style.transform).toBe('')
    fireEvent.touchEnd(dialog)
    expect(onClose).not.toHaveBeenCalled()

    fireEvent.touchStart(dialog, { touches: [{ clientY: 100 }] })
    fireEvent.touchMove(dialog, { touches: [{ clientY: 201 }] })
    fireEvent.touchEnd(dialog)
    expect(onClose).toHaveBeenCalledTimes(1)

    fireEvent.touchStart(dialog, { touches: [{ clientY: 300 }] })
    fireEvent.touchEnd(dialog)
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('consumes repeated close events while React is still unmounting the modal', () => {
    const onClose = vi.fn()
    render(
      <BottomSheet open onClose={onClose} title="Single close">
        Content
      </BottomSheet>,
    )

    fireEvent.keyDown(document, { key: 'Escape' })
    fireEvent.keyDown(document, { key: 'Escape' })

    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
