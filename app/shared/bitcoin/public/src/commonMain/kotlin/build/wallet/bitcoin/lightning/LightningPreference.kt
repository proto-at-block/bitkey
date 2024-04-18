package build.wallet.bitcoin.lightning

import kotlinx.coroutines.flow.Flow

/**
 * Local preference for whether lightning should be running.
 *
 * Currently for debugging purposes only.
 */
interface LightningPreference {
  suspend fun get(): Boolean

  suspend fun set(enabled: Boolean)

  fun isEnabled(): Flow<Boolean>
}
