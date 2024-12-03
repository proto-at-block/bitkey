package build.wallet.logging

import build.wallet.logging.LogLevel.*
import co.touchlab.kermit.Severity

/**
 * Represents the severity level of a log message.
 */
enum class LogLevel {
  /**
   * Used for the most detailed and granular log messages, mostly for deep debugging purposes.
   * Ideal for diagnosing problems and tracing the app's flow.
   *
   * Examples:
   * - Detailed internal state information
   * - Step-by-step execution tracing
   * - Low-level operations
   */
  Verbose,

  /**
   * Used for general debugging information.
   * Helps understand the app's state and diagnose issues without excessive detail.
   *
   * Examples:
   * - Function entry and exit points
   * - Variable values and state changes
   * - Intermediate processing steps
   */
  Debug,

  /**
   * Used for informational messages that highlight the high level progress of the app.
   * Suitable for tracking normal operations and significant events.
   *
   * Examples:
   * - App lifecycle events (start, stop, resume)
   * - Successful operations (e.g., customer wallet creation, backup completion)
   */
  Info,

  /**
   * Use for potentially harmful situations or unexpected events that do not stop the app.
   * Indicates that something unusual happened and may require attention.
   *
   * Examples:
   * - Deprecated API usage
   * - Recoverable errors or fallback scenarios
   * - Non-critical configuration issues
   */
  Warn,

  /**
   * Use for error events that might still allow the app to continue running.
   * Indicates serious issues that need attention and may affect functionality.
   *
   * Examples:
   * - Exceptions that are caught but signify a problem
   * - Failed operations impacting customer experience
   * - Critical issues requiring investigation
   */
  Error,

  /**
   * Use for assertions that should never fail during normal operation.
   * Represents critical errors that indicate a serious problem, often leading to app termination.
   * Typically used in development to enforce invariants and validate assumptions.
   *
   * Examples:
   * - Conditions that must always be true
   * - Critical failures that should halt the app
   * - Internal consistency checks
   */
  Assert,
}

/**
 * Maps our own [LogLevel] type to Kermit's [Severity].
 */
fun LogLevel.toKermitSeverity(): Severity {
  return when (this) {
    Verbose -> Severity.Verbose
    Debug -> Severity.Debug
    Info -> Severity.Info
    Warn -> Severity.Warn
    Error -> Severity.Error
    Assert -> Severity.Assert
  }
}
