package bitkey.securitycenter

import bitkey.metrics.MetricDefinition
import bitkey.metrics.MetricName

/**
 * Tracks the processing of a [SecurityAction], from factory creation to
 * generation of recommendations.
 *
 * @see [SecurityActionMetricDefinition]
 */
data class SecurityActionMetricDefinition(val actionType: SecurityActionType) : MetricDefinition {
  override val name = MetricName("${actionType.name.lowercase()}_check")
}
