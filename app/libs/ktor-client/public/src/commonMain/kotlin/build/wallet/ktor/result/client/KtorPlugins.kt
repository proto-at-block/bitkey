package build.wallet.ktor.result.client

import build.wallet.logging.logDebug
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.logging.Logger as KtorLogger
import io.ktor.client.plugins.logging.Logging as KtorLogging

/**
 * Installs Ktor logging plugin hooked up to Kermit logger.
 */
fun <T : HttpClientEngineConfig> HttpClientConfig<T>.installLogging(
  tag: String,
  logLevel: LogLevel,
) {
  install(KtorLogging) {
    level = logLevel
    logger = object : KtorLogger {
      override fun log(message: String) {
        logDebug(tag = tag) { message }
      }
    }
  }
}
