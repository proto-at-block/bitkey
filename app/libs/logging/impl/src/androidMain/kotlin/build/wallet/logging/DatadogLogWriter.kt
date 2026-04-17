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
    val sensitiveDataResult = SensitiveDataValidator.check(LogEntry(tag, message))
    val safeMessage = when (sensitiveDataResult) {
      SensitiveDataResult.NoneFound -> message
      is SensitiveDataResult.Sensitive -> sensitiveDataResult.redactedMessage
    }
    val safeTag = when (sensitiveDataResult) {
      SensitiveDataResult.NoneFound -> tag
      is SensitiveDataResult.Sensitive -> sensitiveDataResult.redactedTag
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
          throwable = throwable,
          attributes = defaultAttributes
        )
      Severity.Debug ->
        datadogLogger.d(
          message = safeMessage,
          throwable = throwable,
          attributes = defaultAttributes
        )
      Severity.Info ->
        datadogLogger.i(
          message = safeMessage,
          throwable = throwable,
          attributes = defaultAttributes
        )
      Severity.Warn ->
        datadogLogger.w(
          message = safeMessage,
          throwable = throwable,
          attributes = defaultAttributes
        )
      Severity.Error ->
        datadogLogger.e(
          message = safeMessage,
          throwable = throwable,
          attributes = defaultAttributes
        )
      Severity.Assert ->
        datadogLogger.wtf(
          message = safeMessage,
          throwable = throwable,
          attributes = defaultAttributes
        )
    }

    if (severity == Severity.Error || severity == Severity.Assert) {
      // Keep stack context for message-only handled errors.
      datadogRumMonitor.addError(
        message = safeMessage,
        source = ErrorSource.Logger,
        attributes = defaultAttributes,
        cause = throwable ?: SyntheticHandledError(safeMessage)
      )
    }
  }
}

// Used when callers log an error without a throwable.
private class SyntheticHandledError(
  override val message: String,
) : Throwable(message)

private data class UserProperties(
  val appInstallationId: String?,
  val hardwareSerialNumber: String?,
  val firmwareVersion: String?,
)
