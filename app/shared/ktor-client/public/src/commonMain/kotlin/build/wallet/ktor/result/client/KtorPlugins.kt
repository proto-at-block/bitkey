package build.wallet.ktor.result.client

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger as KtorLogger
import io.ktor.client.plugins.logging.Logging as KtorLogging

/**
 * Installs Ktor logging plugin hooked up to Kermit logger.
 */
fun <T : HttpClientEngineConfig> HttpClientConfig<T>.installLogging(logLevel: LogLevel) {
  install(KtorLogging) {
    level = logLevel
    logger =
      object : KtorLogger {
        override fun log(message: String) {
          build.wallet.logging.log { message }
        }
      }
  }
}
