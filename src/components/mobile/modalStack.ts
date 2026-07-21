type ModalId = symbol

interface ModalEntry {
  closing: boolean
  id: ModalId
  onClose: () => void
  root: HTMLElement
}

const modalStack: ModalEntry[] = []
const isolatedElements = new Set<HTMLElement>()
let previousBodyOverflow: string | null = null

function handleEscape(event: KeyboardEvent): void {
  if (event.key !== 'Escape' || !closeTopModal()) return

  event.preventDefault()
  event.stopPropagation()
}

function lockPage(): void {
  if (typeof document === 'undefined' || modalStack.length !== 0) return

  previousBodyOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  document.addEventListener('keydown', handleEscape)
}

function unlockPage(): void {
  if (typeof document === 'undefined' || modalStack.length !== 0) return

  document.removeEventListener('keydown', handleEscape)
  document.body.style.overflow = previousBodyOverflow ?? ''
  previousBodyOverflow = null
}

function clearModalIsolation(): void {
  for (const element of isolatedElements) element.removeAttribute('inert')
  isolatedElements.clear()
}

function syncModalIsolation(): void {
  clearModalIsolation()

  const topModal = modalStack[modalStack.length - 1]
  if (!topModal?.root.isConnected) return

  let activeBranch = topModal.root
  while (activeBranch.parentElement) {
    const parent = activeBranch.parentElement
    for (const sibling of parent.children) {
      if (
        sibling === activeBranch ||
        !(sibling instanceof HTMLElement) ||
        sibling.hasAttribute('inert')
      ) {
        continue
      }

      sibling.setAttribute('inert', '')
      isolatedElements.add(sibling)
    }

    if (parent === document.body) break
    activeBranch = parent
  }
}

export function registerModal(id: ModalId, onClose: () => void, root: HTMLElement): () => void {
  lockPage()
  modalStack.push({ closing: false, id, onClose, root })
  syncModalIsolation()

  return () => {
    const index = modalStack.findIndex((entry) => entry.id === id)
    if (index >= 0) modalStack.splice(index, 1)
    syncModalIsolation()
    unlockPage()
  }
}

export function isTopModal(id: ModalId): boolean {
  return modalStack[modalStack.length - 1]?.id === id
}

export function closeTopModal(): boolean {
  const topModal = modalStack[modalStack.length - 1]
  if (!topModal) return false
  if (topModal.closing) return true

  topModal.closing = true
  try {
    topModal.onClose()
  } catch (error) {
    topModal.closing = false
    throw error
  }
  return true
}
