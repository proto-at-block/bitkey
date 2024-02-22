package build.wallet.logging.dev

import build.wallet.logging.KermitLogWriter

/**
 * [KermitLogWriter] that persists logs into [LogStore].
 */
abstract class LogStoreWriter : KermitLogWriter()
