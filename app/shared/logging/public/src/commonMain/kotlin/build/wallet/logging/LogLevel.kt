package build.wallet.logging

import build.wallet.logging.LogLevel.Assert
import build.wallet.logging.LogLevel.Debug
import build.wallet.logging.LogLevel.Error
import build.wallet.logging.LogLevel.Info
import build.wallet.logging.LogLevel.Verbose
import build.wallet.logging.LogLevel.Warn

/**
 * Represents logging level, mapped directly to Kermit's [KermitSeverity] using [toKermitSeverity].
 */
enum class LogLevel {
  Verbose,
  Debug,
  Info,
  Warn,
  Error,
  Assert,
}

/**
 * Maps our own [LogLevel] type to Kermit's [KermitSeverity].
 */
fun LogLevel.toKermitSeverity(): KermitSeverity {
  return when (this) {
    Verbose -> KermitSeverity.Verbose
    Debug -> KermitSeverity.Debug
    Info -> KermitSeverity.Info
    Warn -> KermitSeverity.Warn
    Error -> KermitSeverity.Error
    Assert -> KermitSeverity.Assert
  }
}
