package bitkey.metrics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class MetricTrackerServiceFake : MetricTrackerService {
  private val metrics = MutableStateFlow<List<TrackedMetric>>(emptyList())

  private val completedMetrics = mutableListOf<CompletedMetric>()

  override fun <T : MetricDefinition> startMetric(
    metricDefinition: T,
    variant: Variant<T>?,
  ) {
    // If metric already exists, mark it as timed out and remove it before adding new one
    metrics.value.find { it.name == metricDefinition.name }?.let { existingMetric ->
      metrics.update { it.filter { metric -> metric.name != metricDefinition.name } }
      completedMetrics.add(
        CompletedMetric(
          metric = existingMetric,
          outcome = MetricOutcome.Timeout
        )
      )
    }

    metrics.update { currentMetrics ->
      currentMetrics + TrackedMetric(
        name = metricDefinition.name,
        variant = variant?.name
      )
    }
  }

  override fun <T : MetricDefinition> setVariant(
    metricDefinition: T,
    variant: Variant<T>,
  ) {
    metrics.update { currentMetrics ->
      currentMetrics.map {
        if (it.name == metricDefinition.name) {
          it.copy(variant = variant.name)
        } else {
          it
        }
      }
    }
  }

  override fun completeMetric(
    metricDefinition: MetricDefinition,
    outcome: MetricOutcome,
  ) {
    metrics.value.find { it.name == metricDefinition.name }?.let { metric ->
      metrics.update { it.filter { m -> m.name != metricDefinition.name } }
      completedMetrics.add(
        CompletedMetric(
          metric = metric,
          outcome = outcome
        )
      )
    }
  }

  override suspend fun clearMetrics() {
    reset()
  }

  /**
   * Test helper to get all completed metrics with their outcomes
   */
  fun getCompletedMetrics(): List<CompletedMetric> = completedMetrics.toList()

  fun reset() {
    metrics.value = emptyList()
    completedMetrics.clear()
  }

  data class CompletedMetric(
    val metric: TrackedMetric,
    val outcome: MetricOutcome,
  )
}
