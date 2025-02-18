package build.wallet.testing.ext

import build.wallet.account.AccountStatus
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.testing.AppTester
import build.wallet.withRealTimeout
import com.github.michaelbull.result.getOrThrow
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

suspend fun AppTester.getActiveAppGlobalAuthKey(): AppKey<AppGlobalAuthKey> {
  val account = getActiveFullAccount()
  val appGlobalAuthPublicKey = account.keybox.activeAppKeyBundle.authKey
  val appGlobalAuthPrivateKey =
    requireNotNull(
      appPrivateKeyDao.getAsymmetricPrivateKey(appGlobalAuthPublicKey).getOrThrow()
    )
  return AppKey(appGlobalAuthPublicKey, appGlobalAuthPrivateKey)
}

suspend fun AppTester.getActiveAccount(): Account {
  // TODO: add Flow#realTimeout extension?
  val accountStatus = withRealTimeout(3.seconds) {
    accountService.accountStatus()
      .first().getOrThrow()
  }
  return (accountStatus as? AccountStatus.ActiveAccount)?.account
    ?: error("active account not found")
}

/**
 * Returns and asserts the active keybox
 */
suspend fun AppTester.getActiveFullAccount(): FullAccount {
  return getActiveAccount() as? FullAccount ?: error("active Full Account not found")
}
