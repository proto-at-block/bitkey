package build.wallet.logging.dev

import co.touchlab.kermit.LogWriter

/**
 * [LogWriter] that persists logs into [LogStore].
 */
abstract class LogStoreWriter : LogWriter()
