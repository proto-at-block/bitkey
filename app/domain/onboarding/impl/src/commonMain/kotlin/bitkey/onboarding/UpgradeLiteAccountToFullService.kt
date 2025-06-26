package bitkey.onboarding

import bitkey.auth.AuthTokenScope
import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AuthTokensService
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.keybox.KeyCrossDraft
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.onboarding.UpgradeAccountF8eClient
import build.wallet.keybox.KeyboxDao
import build.wallet.notifications.DeviceTokenManager
import build.wallet.platform.random.UuidGenerator
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError

interface UpgradeLiteAccountToFullService {
  /**
   * Upgrades the given Lite Account to a Full Account with brand new app and hardware keys
   * (except for keys that the Lite Account already had, like recovery auth key).
   */
  suspend fun upgradeAccount(
    liteAccount: LiteAccount,
    keyCrossDraft: KeyCrossDraft.WithAppKeysAndHardwareKeys,
  ): Result<FullAccount, FullAccountCreationError>
}

@BitkeyInject(AppScope::class)
class UpgradeLiteToFullAccountServiceImpl(
  private val accountAuthenticator: AccountAuthenticator,
  private val authTokensService: AuthTokensService,
  private val deviceTokenManager: DeviceTokenManager,
  private val keyboxDao: KeyboxDao,
  private val upgradeAccountF8eClient: UpgradeAccountF8eClient,
  private val uuidGenerator: UuidGenerator,
) : UpgradeLiteAccountToFullService {
  override suspend fun upgradeAccount(
    liteAccount: LiteAccount,
    keyCrossDraft: KeyCrossDraft.WithAppKeysAndHardwareKeys,
  ): Result<FullAccount, FullAccountCreationError> =
    coroutineBinding {
      val fullAccountConfig = keyCrossDraft.config
      // Upgrade the account on the server and get a server key back.
      val (f8eSpendingKeyset, accountId) =
        upgradeAccountF8eClient
          .upgradeAccount(liteAccount, keyCrossDraft)
          .mapError { FullAccountCreationError.AccountCreationF8eError(it) }
          .bind()

      val spendingKeyset =
        SpendingKeyset(
          localId = uuidGenerator.random(),
          appKey = keyCrossDraft.appKeyBundle.spendingKey,
          networkType = fullAccountConfig.bitcoinNetworkType,
          hardwareKey = keyCrossDraft.hardwareKeyBundle.spendingKey,
          f8eSpendingKeyset = f8eSpendingKeyset
        )

      // Store the [Global] scope auth tokens (we don't need to do the [Recovery] scope because
      // the Lite Account already has those stored.
      val authTokens =
        accountAuthenticator
          .appAuth(
            appAuthPublicKey = keyCrossDraft.appKeyBundle.authKey,
            authTokenScope = AuthTokenScope.Global
          )
          .mapError { FullAccountCreationError.AccountCreationAuthError(it) }
          .bind()
          .authTokens

      authTokensService
        .setTokens(accountId, authTokens, AuthTokenScope.Global)
        .mapError { FullAccountCreationError.AccountCreationDatabaseError.FailedToSaveAuthTokens(it) }
        .bind()

      // Don't bind the error so we don't block account creation on the success of adding
      // the device token because it won't yet be available on iOS until push notification
      // permissions are requested (which happens after full account creation).
      deviceTokenManager
        .addDeviceTokenIfPresentForAccount(
          fullAccountId = accountId,
          authTokenScope = AuthTokenScope.Global
        )

      // Retain previous recovery auth key.
      val adjustedKeyCross = keyCrossDraft.appKeyBundle
        .copy(recoveryAuthKey = liteAccount.recoveryAuthKey)

      // We now have everything we need for our Keyset (app/hw/server spending keys)
      val keybox =
        Keybox(
          localId = uuidGenerator.random(),
          fullAccountId = accountId,
          activeSpendingKeyset = spendingKeyset,
          activeAppKeyBundle = adjustedKeyCross,
          activeHwKeyBundle = keyCrossDraft.hardwareKeyBundle,
          appGlobalAuthKeyHwSignature = keyCrossDraft.appGlobalAuthKeyHwSignature,
          config = fullAccountConfig
        )

      // Save our keybox, but do NOT set as active.
      // Once onboarding completes, it will be activated
      keyboxDao.saveKeyboxAndBeginOnboarding(keybox)
        .mapError { FullAccountCreationError.AccountCreationDatabaseError.FailedToSaveKeybox(it) }
        .bind()

      FullAccount(accountId, keybox.config, keybox)
    }
}
