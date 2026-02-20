package build.wallet.logging

/**
 * Represents various attributes that can be included by various log writers (for example Datadog
 * log writer to include "app user info").
 *
 * Note: [appSessionId] matches the `session_id` used by `EventTracker` analytics events (via
 * `AppSessionManager.getSessionId()`), and can be used to correlate logs with analytics.
 * This is distinct from Datadog's RUM session id.
 */
data class LogWriterContext(
  val appInstallationId: String? = null,
  val appSessionId: String? = null,
  val hardwareSerialNumber: String? = null,
  val firmwareVersion: String? = null,
)
