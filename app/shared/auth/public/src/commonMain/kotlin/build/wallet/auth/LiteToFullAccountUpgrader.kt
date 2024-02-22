package build.wallet.auth

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.keybox.KeyCrossDraft
import com.github.michaelbull.result.Result

interface LiteToFullAccountUpgrader {
  /**
   * Upgrades the given Lite Account to a Full Account with brand new app and hardware keys
   * (except for keys that the Lite Account already had, like recovery auth key).
   */
  suspend fun upgradeAccount(
    liteAccount: LiteAccount,
    keyCrossDraft: KeyCrossDraft.WithAppKeysAndHardwareKeys,
  ): Result<FullAccount, AccountCreationError>
}
