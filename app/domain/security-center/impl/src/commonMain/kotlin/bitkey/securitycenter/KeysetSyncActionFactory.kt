package bitkey.securitycenter

import build.wallet.account.AccountService
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.KeysetRepairFeatureFlag
import build.wallet.recovery.keyset.SpendingKeysetRepairService
import build.wallet.recovery.keyset.SpendingKeysetSyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Factory that creates [SpendingKeysetSyncAction] based on keyset sync status.
 */
interface KeysetSyncActionFactory {
  /**
   * Creates a flow of [SecurityAction] for keyset sync status.
   */
  suspend fun create(): Flow<SecurityAction>
}

@BitkeyInject(AppScope::class)
class KeysetSyncActionFactoryImpl(
  private val spendingKeysetRepairService: SpendingKeysetRepairService,
  private val accountService: AccountService,
  private val keysetRepairFeatureFlag: KeysetRepairFeatureFlag,
) : KeysetSyncActionFactory {
  override suspend fun create(): Flow<SecurityAction> =
    combine(
      accountService.activeAccount(),
      spendingKeysetRepairService.syncStatus,
      keysetRepairFeatureFlag.flagValue().map { it.value }
    ) { account, syncStatus, flagEnabled ->
      if (account is FullAccount && flagEnabled) {
        SpendingKeysetSyncAction(syncStatus)
      } else {
        // When feature flag is disabled or no full account, return a no-op action
        SpendingKeysetSyncAction(SpendingKeysetSyncStatus.Synced)
      }
    }
}
