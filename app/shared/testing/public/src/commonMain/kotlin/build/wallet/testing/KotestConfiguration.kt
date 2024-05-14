package build.wallet.testing

import build.wallet.logging.LogLevel
import build.wallet.logging.Logger
import build.wallet.logging.platformLogWriter
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * This configuration is picked up by Kotest at runtime: https://kotest.io/docs/framework/project-config.html.
 *
 * Note that this only works with JVM/Android tests.
 */
@Suppress("unused")
internal object KotestConfiguration : AbstractProjectConfig() {
  /**
   * Batch all assertions together to make it easier to debug test failures.
   */
  override val globalAssertSoftly = true

  /**
   * Enable enhanced logs tracing of coroutines when an error occurs.
   */
  override val coroutineDebugProbes = false

  /**
   * All tests in the module must complete within the timeout
   */
  override val projectTimeout: Duration = 10.minutes

  init {
    // Initialize logger with our own log writers for testing purposes.
    Logger.configure(
      tag = "build.wallet",
      minimumLogLevel = LogLevel.Debug,
      logWriters = listOf(TestLogStoreWriter, platformLogWriter())
    )
  }

  override fun extensions(): List<Extension> =
    listOf(
      SensitiveDataLogListener()
    )
}
