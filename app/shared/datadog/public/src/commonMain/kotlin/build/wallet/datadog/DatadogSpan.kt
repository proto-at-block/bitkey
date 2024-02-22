package build.wallet.datadog

/**
 * This interface represents the definition for native implementations of the Datadog span object
 *
 * https://docs.datadoghq.com/tracing/trace_collection/dd_libraries/android/?tab=kotlin
 * https://docs.datadoghq.com/tracing/trace_collection/dd_libraries/ios/?tab=cocoapods
 *
 */
interface DatadogSpan {
  /**
   * The name of the resource associated with the current span.
   *
   * See https://docs.datadoghq.com/tracing/glossary/#resources for more info
   */
  var resourceName: String?

  /**
   * Set a tag on the span
   */
  fun setTag(
    key: String,
    value: String,
  )

  /**
   * Finish the span once the operation is complete
   */
  fun finish()

  /**
   * Finish the span in an error state
   *
   * @param cause - The exception that caused the operation to fail
   */
  fun finish(cause: Throwable)
}
