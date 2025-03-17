package build.wallet.testing

import build.wallet.logging.LogLevel
import build.wallet.logging.Logger
import build.wallet.logging.platformLogWriter
import build.wallet.testing.extensions.RetryFlakyTestsExtension
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * This configuration is picked up by Kotest at runtime: https://kotest.io/docs/framework/project-config.html.
 *
 * Note that this only works with JVM/Android tests.
 */
@Suppress("unused")
internal object KotestProjectConfig : AbstractProjectConfig() {
  /**
   * All tests in the module must complete within the timeout
   */
  override val projectTimeout: Duration = 10.minutes

  init {
    // Initialize logger with our own log writers for testing purposes.
    Logger.configure(
      tag = "build.wallet",
      /**
       * Use Info as default logging level for integration tests - anything lower than that is too noisy.
       * Update this log level for local development/testing purposes as needed.
       */
      minimumLogLevel = LogLevel.Info,
      logWriters = listOf(TestLogStoreWriter, platformLogWriter())
    )
  }

  override fun extensions(): List<Extension> =
    listOf(
      SensitiveDataLogListener(),
      RetryFlakyTestsExtension(attempts = 3, timeout = 1.minutes, delay = 10.seconds)
    )
}
