package build.wallet.logging

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.datadog.android.Datadog
import com.datadog.android.log.Logger

@BitkeyInject(AppScope::class)
class DatadogLogWriter(
  private val logWriterContextStore: LogWriterContextStore,
) : LogWriter() {
  private val minSeverity = Severity.Info

  private val datadogLogger: Logger by lazy {
    val logWriterContext = logWriterContextStore.get()
    Datadog.addUserProperties(
      mapOf(
        "app_installation_id" to logWriterContext.appInstallationId,
        "hardware_serial_number" to logWriterContext.hardwareSerialNumber,
        "firmware_version" to logWriterContext.firmwareVersion
      )
    )
    Logger
      .Builder()
      .setNetworkInfoEnabled(enabled = true)
      .setBundleWithTraceEnabled(enabled = true)
      .setBundleWithRumEnabled(enabled = true)
      .setRemoteSampleRate(sampleRate = 100f)
      .build()
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
      mapOf(
        "tag" to safeTag
      )
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
  }
}
