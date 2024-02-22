package build.wallet.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

/**
 * Typealiases for Kermit APIs that do not conflict with our own APIs that use the same naming.
 */
internal typealias KermitLogger = Logger
internal typealias KermitSeverity = Severity
internal typealias KermitLogWriter = LogWriter
