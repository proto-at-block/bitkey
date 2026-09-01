package build.wallet.logging

import bitkey.datadog.DatadogRumMonitor
import bitkey.datadog.ErrorSource
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.datadog.android.Datadog
import com.datadog.android.log.Logger

@BitkeyInject(AppScope::class)
class DatadogLogWriter(
  private val logWriterContextStore: LogWriterContextStore,
  private val datadogRumMonitor: DatadogRumMonitor,
) : LogWriter() {
  private val minSeverity = Severity.Info

  private val datadogLogger: Logger by lazy {
    Logger
      .Builder()
      .setNetworkInfoEnabled(enabled = true)
      .setBundleWithTraceEnabled(enabled = true)
      .setBundleWithRumEnabled(enabled = true)
      .setRemoteSampleRate(sampleRate = 100f)
      .build()
  }

  private val userPropertiesLock = Any()
  private var lastUserProperties: UserProperties? = null

  private fun refreshUserPropertiesIfNeeded(context: LogWriterContext) {
    synchronized(userPropertiesLock) {
      val current = UserProperties(
        appInstallationId = context.appInstallationId,
        hardwareSerialNumber = context.hardwareSerialNumber,
        firmwareVersion = context.firmwareVersion
      )
      if (current != lastUserProperties) {
        lastUserProperties = current
        Datadog.addUserProperties(
          mapOf(
            "app_installation_id" to current.appInstallationId,
            "hardware_serial_number" to current.hardwareSerialNumber,
            "firmware_version" to current.firmwareVersion
          )
        )
      }
    }
  }

  override fun isLoggable(
    tag: String,
    severity: Severity,
  ): Boolean = severity >= minSeverity

  override fun log(
    severity: Severity,
    message: String,
    tag: String,
    throwable: Throwable?,
  ) {
    val logContext = logWriterContextStore.get()
    refreshUserPropertiesIfNeeded(logContext)
    // Check the throwable's full cause chain along with the tag/message: exception messages
    // can embed the same sensitive material and are uploaded with the log.
    val sensitiveDataResult = SensitiveDataValidator.check(
      entry = LogEntry(tag, message),
      throwable = throwable
    )
    val safeMessage: String
    val safeTag: String
    val safeThrowable: Throwable?
    when (sensitiveDataResult) {
      SensitiveDataResult.NoneFound -> {
        safeMessage = message
        safeTag = tag
        safeThrowable = throwable
      }
      is SensitiveDataResult.Sensitive -> {
        safeMessage = sensitiveDataResult.redactedMessage
        safeTag = sensitiveDataResult.redactedTag
        // Drop the throwable: its message (and the stack trace that embeds it) may contain
        // the sensitive data that triggered redaction. Replace with a synthetic error so
        // error-level semantics are preserved in Datadog.
        safeThrowable = throwable?.let { RedactedThrowable() }
      }
    }

    val defaultAttributes =
      buildMap {
        put("tag", safeTag)
        logContext.appSessionId?.let { put("app_session_id", it) }
      }
    when (severity) {
      Severity.Verbose ->
        datadogLogger.v(
          message = safeMessage,
          throwable = safeThrowable,
          attributes = defaultAttributes
        )
      Severity.Debug ->
        datadogLogger.d(
          message = safeMessage,
          throwable = safeThrowable,
          attributes = defaultAttributes
        )
      Severity.Info ->
        datadogLogger.i(
          message = safeMessage,
          throwable = safeThrowable,
          attributes = defaultAttributes
        )
      Severity.Warn ->
        datadogLogger.w(
          message = safeMessage,
          throwable = safeThrowable,
          attributes = defaultAttributes
        )
      Severity.Error ->
        datadogLogger.e(
          message = safeMessage,
          throwable = safeThrowable,
          attributes = defaultAttributes
        )
      Severity.Assert ->
        datadogLogger.wtf(
          message = safeMessage,
          throwable = safeThrowable,
          attributes = defaultAttributes
        )
    }

    if (severity == Severity.Error || severity == Severity.Assert) {
      // Keep stack context for message-only handled errors.
      datadogRumMonitor.addError(
        message = safeMessage,
        source = ErrorSource.Logger,
        attributes = defaultAttributes,
        cause = safeThrowable ?: SyntheticHandledError(safeMessage)
      )
    }
  }
}

// Used when callers log an error without a throwable.
private class SyntheticHandledError(
  override val message: String,
) : Throwable(message)

/**
 * Replaces a throwable whose message (or the stack trace embedding it) may contain sensitive
 * data. Carries no message or stack information from the original.
 */
private class RedactedThrowable : Throwable("REDACTED - Possible sensitive data in throwable")

private data class UserProperties(
  val appInstallationId: String?,
  val hardwareSerialNumber: String?,
  val firmwareVersion: String?,
)
