package build.wallet.statemachine.core

import build.wallet.logging.*

/**
 * Information about the error that caused an error screen to display.
 */
data class ErrorData(
  /**
   * The segment of the application the user is currently attempting to use.
   */
  val segment: AppSegment,
  /**
   * A brief description of the action the user was taking that led to this error.
   *
   * For example: "Rotating the app auth key".
   */
  val actionDescription: String,
  /**
   * An error cause with a stacktrace that can be logged in addition to the message.
   */
  val cause: Throwable,
)

/**
 * send a log using the current error data.
 */
fun ErrorData?.log() {
  if (this == null) return
  logError(
    tag = segment.id,
    throwable = cause
  ) {
    "[ErrorData] Action: $actionDescription Error: ${cause::class.simpleName} Message: ${cause.message}"
  }
}
