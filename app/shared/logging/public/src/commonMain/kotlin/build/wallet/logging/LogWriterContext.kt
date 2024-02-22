package build.wallet.logging

/**
 * Represents various attributes that can be included by various log writers (for example Datadog
 * log writer to include "app user info").
 */
data class LogWriterContext(
  val appInstallationId: String? = null,
  val hardwareSerialNumber: String? = null,
)
