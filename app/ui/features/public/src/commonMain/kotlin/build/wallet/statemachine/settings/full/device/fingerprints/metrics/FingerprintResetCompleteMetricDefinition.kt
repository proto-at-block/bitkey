package build.wallet.statemachine.settings.full.device.fingerprints.metrics

import bitkey.metrics.MetricDefinition
import bitkey.metrics.MetricName

/**
 * Tracks when the fingerprint reset process is completed successfully.
 */
data object FingerprintResetCompleteMetricDefinition : MetricDefinition {
  override val name = MetricName("fingerprint_reset_complete")
}
