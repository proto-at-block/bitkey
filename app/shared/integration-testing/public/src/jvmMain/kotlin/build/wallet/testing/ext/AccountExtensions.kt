package build.wallet.testing.ext

import build.wallet.account.AccountStatus
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.testing.AppTester
import com.github.michaelbull.result.getOrThrow
import kotlinx.coroutines.flow.first

suspend fun AppTester.getActiveAppGlobalAuthKey(): AppKey<AppGlobalAuthKey> {
  val account = getActiveFullAccount()
  val appGlobalAuthPublicKey = account.keybox.activeAppKeyBundle.authKey
  val appGlobalAuthPrivateKey =
    requireNotNull(
      app.appComponent.appPrivateKeyDao.getAsymmetricPrivateKey(appGlobalAuthPublicKey).getOrThrow()
    )
  return AppKey(appGlobalAuthPublicKey, appGlobalAuthPrivateKey)
}

/**
 * Returns and asserts the active lite account
 */
suspend fun AppTester.getActiveLiteAccount(): LiteAccount {
  return getActiveAccount() as? LiteAccount ?: error("active Lite Account not found")
}

suspend fun AppTester.getActiveAccount(): Account {
  val accountStatus = app.appComponent.accountRepository.accountStatus().first().getOrThrow()
  return (accountStatus as? AccountStatus.ActiveAccount)?.account ?: error("active account not found")
}

/**
 * Returns and asserts the active keybox
 */
suspend fun AppTester.getActiveFullAccount(): FullAccount {
  return getActiveAccount() as? FullAccount ?: error("active Full Account not found")
}
