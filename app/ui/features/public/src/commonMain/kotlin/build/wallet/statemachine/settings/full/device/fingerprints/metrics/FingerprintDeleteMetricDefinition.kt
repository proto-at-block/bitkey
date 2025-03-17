package build.wallet.statemachine.settings.full.device.fingerprints.metrics

import bitkey.metrics.MetricDefinition
import bitkey.metrics.MetricName

/**
 * Tracks the user journey of adding a new fingerprint to hardware.
 */
data object FingerprintDeleteMetricDefinition : MetricDefinition {
  override val name = MetricName("fingerprint_delete")
}
