package build.wallet.limit

import build.wallet.bitkey.account.FullAccount
import kotlinx.coroutines.flow.Flow

interface MobilePayStatusRepository {
  /**
   * Refreshes mobile pay limits when mobile pay is enabled.
   */
  suspend fun refreshStatus()

  /**
   * Provides currently active spending limit for active account.
   */
  fun status(account: FullAccount): Flow<MobilePayStatus>
}
