package build.wallet.logging

import co.touchlab.kermit.LogWriter

/**
 * Same as [co.touchlab.kermit.platformLogWriter] but on JVM uses [ColorfulMessageStringFormatter].
 * Android and iOS already print colorful logs by default.
 */
expect fun platformLogWriter(): LogWriter
