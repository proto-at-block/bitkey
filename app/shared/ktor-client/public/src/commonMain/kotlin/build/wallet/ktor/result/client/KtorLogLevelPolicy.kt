package build.wallet.ktor.result.client

import build.wallet.platform.config.AppVariant
import io.ktor.client.plugins.logging.LogLevel

interface KtorLogLevelPolicy {
  /**
   * Determines Ktor http log level based on [AppVariant].
   */
  fun level(): LogLevel
}
