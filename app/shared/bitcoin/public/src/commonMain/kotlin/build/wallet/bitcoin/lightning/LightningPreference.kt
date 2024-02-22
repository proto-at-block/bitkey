package build.wallet.bitcoin.lightning

/**
 * Local preference for whether lightning should be running.
 *
 * Currently for debugging purposes only.
 */
interface LightningPreference {
  suspend fun get(): Boolean

  suspend fun set(enabled: Boolean)
}
