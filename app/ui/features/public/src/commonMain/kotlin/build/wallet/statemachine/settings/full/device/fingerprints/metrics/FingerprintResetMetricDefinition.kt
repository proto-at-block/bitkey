package build.wallet.statemachine.settings.full.device.fingerprints.metrics

import bitkey.metrics.MetricDefinition
import bitkey.metrics.MetricName

/**
 * Tracks the user journey of resetting fingerprints on hardware through the delay + notify process.
 */
data object FingerprintResetMetricDefinition : MetricDefinition {
  override val name = MetricName("fingerprint_reset")
}
