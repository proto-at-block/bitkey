package bitkey.metrics

import kotlin.jvm.JvmInline

/**
 * Defines a mobile real-time tracked metric
 *
 * It is recommended that you implement this interface and any associated
 * [Variant]s in a single file, to provide an at-a-glance view of all details
 * of the metric.
 *
 * An implementation may look something like:
 *
 * data object MyMetricDefinition : MetricDefinition {
 *   override val name = MetricName("my_metric)"
 *
 *
 *   sealed class Variants(override val name: String) : Variant<MyMetricDefinition> {
 *     data object Variant1 : Variants("variant_1")
 *
 *     data object Variant2 : Variants("variant_2")
 *   }
 * }
 */
interface MetricDefinition {
  val name: MetricName
}

/**
 * The name of the metric. Naming convention is entity_action, e.g. fingerprint_add, partnership_sell
 *
 * Should be lower_snake_case.
 */
@JvmInline
value class MetricName(val name: String)

/**
 * Indicates there are multiple paths within a user journey. For example, this might be the transfer
 * partner during the transfer flow.
 */
interface Variant<T : MetricDefinition> {
  /**
   * The name of the variant. Should be in lower_snake_case.
   */
  val name: String
}
