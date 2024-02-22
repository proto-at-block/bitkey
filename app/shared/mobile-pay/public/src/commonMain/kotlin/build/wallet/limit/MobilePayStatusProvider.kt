package build.wallet.limit

import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.keybox.Keybox
import kotlinx.coroutines.flow.Flow

interface MobilePayStatusProvider {
  /**
   * Refreshes mobile pay limits when mobile pay is enabled.
   */
  suspend fun refreshStatus()

  /**
   * Provides currently active spending limit for active keybox.
   */
  fun status(
    keybox: Keybox,
    wallet: SpendingWallet,
  ): Flow<MobilePayStatus>
}
