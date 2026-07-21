import { describe, expect, it, vi } from 'vitest'
import { createLogger } from '../logger'

describe('createLogger', () => {
  it('writes scoped structured messages without exposing raw error objects', () => {
    const info = vi.spyOn(console, 'info').mockImplementation(() => undefined)
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    const error = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const debug = vi.spyOn(console, 'debug').mockImplementation(() => undefined)
    const logger = createLogger('Test')

    logger.debug('debug message', { attempt: 1 })
    logger.info('info message', { enabled: true })
    logger.warn('warning', new Error('safe summary'))
    logger.error('failure', 'not-an-error')

    expect(info).toHaveBeenCalledWith('[Test]', 'info message', { enabled: true })
    expect(warn).toHaveBeenCalledWith('[Test]', 'warning', { error: 'safe summary' })
    expect(error).toHaveBeenCalledWith('[Test]', 'failure', { error: 'Unknown error' })
    expect(debug).toHaveBeenCalledTimes(import.meta.env.DEV ? 1 : 0)
  })
})
