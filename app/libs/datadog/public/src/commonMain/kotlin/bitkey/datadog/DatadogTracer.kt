package bitkey.datadog

/**
 * This interface represents the definition for native implementations of the Datadog tracer library
 *
 * https://docs.datadoghq.com/tracing/trace_collection/dd_libraries/android/?tab=kotlin
 * https://docs.datadoghq.com/tracing/trace_collection/dd_libraries/ios/?tab=cocoapods
 *
 */
interface DatadogTracer {
  /**
   * Build and start a span for tracing
   *
   * @param spanName - A string to represent the span
   * @return [DatadogSpan] - a interface representing a native span
   */
  fun buildSpan(spanName: String): DatadogSpan

  /**
   * Build a sub-span for tracing
   *
   * @param spanName - A string to represent the span
   * @param parentSpan - The parent span to be associated with the sub-span
   * @return [DatadogSpan] - a interface representing a native span
   */
  fun buildSpan(
    spanName: String,
    parentSpan: DatadogSpan,
  ): DatadogSpan

  /**
   * Inject a span into an operation such as network call
   *
   * @param span the span to be traced
   * @return [TracerHeaders] Headers to be used and associated with a network call
   */
  fun inject(span: DatadogSpan): TracerHeaders
}
