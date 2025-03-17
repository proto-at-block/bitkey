package build.wallet.queueprocessor

/**
 * Minimal interface for starting a long running batch processing job
 */
interface PeriodicProcessor {
  suspend fun start()
}
