type LogContext = Record<string, boolean | number | string | null | undefined>

function formatError(error: unknown): string {
  if (error instanceof Error) return error.message
  return 'Unknown error'
}

interface Logger {
  debug: (message: string, context?: LogContext) => void
  info: (message: string, context?: LogContext) => void
  warn: (message: string, error?: unknown, context?: LogContext) => void
  error: (message: string, error?: unknown, context?: LogContext) => void
}

/**
 * Small, privacy-conscious logger used at application boundaries.
 * Callers must pass structured metadata only; browsing URLs and native
 * configuration values must never be included in a log context.
 */
export function createLogger(scope: string): Logger {
  const prefix = `[${scope}]`

  return {
    debug(message, context) {
      if (import.meta.env.DEV) console.debug(prefix, message, context ?? '')
    },
    info(message, context) {
      console.info(prefix, message, context ?? '')
    },
    warn(message, error, context) {
      console.warn(prefix, message, { ...context, error: formatError(error) })
    },
    error(message, error, context) {
      console.error(prefix, message, { ...context, error: formatError(error) })
    },
  }
}
