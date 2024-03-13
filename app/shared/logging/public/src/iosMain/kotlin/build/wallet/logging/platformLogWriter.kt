package build.wallet.logging

import co.touchlab.kermit.platformLogWriter

/**
 * Delegates to Kermit's [platformLogWriter].
 */
actual fun platformLogWriter() = platformLogWriter()
