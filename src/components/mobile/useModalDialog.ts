import { type KeyboardEvent, type RefObject, useCallback, useEffect, useRef } from 'react'
import { isTopModal, registerModal } from './modalStack'

const FOCUSABLE_SELECTOR = [
  '[autofocus]',
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

function getFocusableElements(dialog: HTMLElement): HTMLElement[] {
  return Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)).filter(
    (element) => element.tabIndex >= 0 && !element.closest('[aria-hidden="true"], [inert]'),
  )
}

interface ModalDialogBehavior {
  onKeyDown: (event: KeyboardEvent<HTMLElement>) => void
}

export function useModalDialog(
  open: boolean,
  onClose: () => void,
  dialogRef: RefObject<HTMLElement | null>,
): ModalDialogBehavior {
  const modalId = useRef(Symbol('modal'))
  const onCloseRef = useRef(onClose)
  onCloseRef.current = onClose

  useEffect(() => {
    if (!open) return

    const previouslyFocused =
      document.activeElement instanceof HTMLElement ? document.activeElement : null
    const dialog = dialogRef.current
    if (!dialog) return

    const modalRoot = dialog.closest<HTMLElement>('[data-modal-root]') ?? dialog
    const unregister = registerModal(modalId.current, () => onCloseRef.current(), modalRoot)

    const keepFocusInside = (event: FocusEvent) => {
      if (
        isTopModal(modalId.current) &&
        event.target instanceof Node &&
        !dialog.contains(event.target)
      ) {
        ;(getFocusableElements(dialog)[0] ?? dialog).focus()
      }
    }
    document.addEventListener('focusin', keepFocusInside)

    if (!dialog.contains(document.activeElement)) {
      const initialFocus = getFocusableElements(dialog)[0] ?? dialog
      initialFocus.focus()
    }

    return () => {
      const shouldRestoreFocus = isTopModal(modalId.current)
      document.removeEventListener('focusin', keepFocusInside)
      unregister()
      if (shouldRestoreFocus && previouslyFocused?.isConnected) previouslyFocused.focus()
    }
  }, [open, dialogRef])

  const onKeyDown = useCallback(
    (event: KeyboardEvent<HTMLElement>) => {
      if (event.key !== 'Tab' || !isTopModal(modalId.current)) return

      const dialog = dialogRef.current
      if (!dialog) return

      const focusableElements = getFocusableElements(dialog)
      if (focusableElements.length === 0) {
        event.preventDefault()
        dialog.focus()
        return
      }

      const firstElement = focusableElements[0]
      const lastElement = focusableElements[focusableElements.length - 1] ?? firstElement
      const activeElement = document.activeElement

      if (event.shiftKey && (activeElement === firstElement || !dialog.contains(activeElement))) {
        event.preventDefault()
        lastElement.focus()
      } else if (!event.shiftKey && activeElement === lastElement) {
        event.preventDefault()
        firstElement.focus()
      }
    },
    [dialogRef],
  )

  return { onKeyDown }
}
