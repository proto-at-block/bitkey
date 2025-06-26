package bitkey.onboarding

import bitkey.auth.AuthTokenScope
import bitkey.onboarding.FullAccountCreationError.AccountCreationAuthError
import bitkey.onboarding.FullAccountCreationError.AccountCreationDatabaseError.FailedToSaveAuthTokens
import bitkey.onboarding.FullAccountCreationError.AccountCreationDatabaseError.FailedToSaveKeybox
import bitkey.onboarding.FullAccountCreationError.AccountCreationF8eError
import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AuthTokensService
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeysAndHardwareKeys
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.notifications.NotificationTouchpointF8eClient
import build.wallet.f8e.onboarding.CreateFullAccountF8eClient
import build.wallet.keybox.KeyboxDao
import build.wallet.notifications.DeviceTokenManager
import build.wallet.notifications.NotificationTouchpointDao
import build.wallet.platform.random.UuidGenerator
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onSuccess

interface CreateFullAccountService {
  /**
   * Given brand new app and hardware keys, create a brand new [FullAccount]
   */
  suspend fun createAccount(
    keyCrossDraft: WithAppKeysAndHardwareKeys,
  ): Result<FullAccount, FullAccountCreationError>
}

@BitkeyInject(AppScope::class)
class CreateFullAccountServiceImpl(
  private val keyboxDao: KeyboxDao,
  private val createFullAccountF8eClient: CreateFullAccountF8eClient,
  private val accountAuthenticator: AccountAuthenticator,
  private val authTokensService: AuthTokensService,
  private val deviceTokenManager: DeviceTokenManager,
  private val uuidGenerator: UuidGenerator,
  private val notificationTouchpointF8eClient: NotificationTouchpointF8eClient,
  private val notificationTouchpointDao: NotificationTouchpointDao,
) : CreateFullAccountService {
  override suspend fun createAccount(
    keyCrossDraft: WithAppKeysAndHardwareKeys,
  ): Result<FullAccount, FullAccountCreationError> =
    coroutineBinding {
      val accountConfig = keyCrossDraft.config
      // Create a new account on the server and get a server key back.
      val (f8eSpendingKeyset, accountId) = createFullAccountF8eClient
        .createAccount(keyCrossDraft)
        .mapError { AccountCreationF8eError(it) }
        .bind()

      val spendingKeyset = SpendingKeyset(
        localId = uuidGenerator.random(),
        appKey = keyCrossDraft.appKeyBundle.spendingKey,
        networkType = accountConfig.bitcoinNetworkType,
        hardwareKey = keyCrossDraft.hardwareKeyBundle.spendingKey,
        f8eSpendingKeyset = f8eSpendingKeyset
      )

      // Store the [Global] scope auth tokens
      authenticateWithF8eAndStoreAuthTokens(
        accountId = accountId,
        appAuthPublicKey = keyCrossDraft.appKeyBundle.authKey,
        tokenScope = AuthTokenScope.Global
      ).bind()

      // Store the [Recovery] scope auth tokens
      authenticateWithF8eAndStoreAuthTokens(
        accountId = accountId,
        appAuthPublicKey = keyCrossDraft.appKeyBundle.recoveryAuthKey,
        tokenScope = AuthTokenScope.Recovery
      ).bind()

      // Don't bind the error so we don't block account creation on the success of adding
      // the device token because it won't yet be available on iOS until push notification
      // permissions are requested (which happens after account creation).
      deviceTokenManager
        .addDeviceTokenIfPresentForAccount(
          fullAccountId = accountId,
          authTokenScope = AuthTokenScope.Global
        )

      // We now have everything we need for our Keyset (app/hw/server spending keys)
      val keybox = Keybox(
        localId = uuidGenerator.random(),
        fullAccountId = accountId,
        activeSpendingKeyset = spendingKeyset,
        activeAppKeyBundle = keyCrossDraft.appKeyBundle,
        activeHwKeyBundle = keyCrossDraft.hardwareKeyBundle,
        appGlobalAuthKeyHwSignature = keyCrossDraft.appGlobalAuthKeyHwSignature,
        config = keyCrossDraft.config
      )

      // Get notification touchpoints in the event that this is an existing account
      // In the event of failure, just continue with account creation.
      // Errors will be logged where they occur.
      notificationTouchpointF8eClient
        .getTouchpoints(accountConfig.f8eEnvironment, accountId)
        .get()
        ?.let { touchpoints ->
          notificationTouchpointDao.clear()
            .onSuccess {
              touchpoints.forEach { touchpoint ->
                notificationTouchpointDao.storeTouchpoint(touchpoint)
              }
            }
        }

      // Save our keybox, but do NOT set as active.
      // Once onboarding completes, it will be activated
      keyboxDao.saveKeyboxAndBeginOnboarding(keybox)
        .mapError { FailedToSaveKeybox(it) }
        .bind()

      FullAccount(accountId, keybox.config, keybox)
    }

  /**
   * Performs auth with f8e using the given [AppAuthPublicKey] and stores the resulting
   * tokens in [AuthTokenDao] keyed by the given [AuthTokenScope]
   */
  private suspend fun authenticateWithF8eAndStoreAuthTokens(
    accountId: FullAccountId,
    appAuthPublicKey: PublicKey<out AppAuthKey>,
    tokenScope: AuthTokenScope,
  ): Result<Unit, FullAccountCreationError> {
    return coroutineBinding {
      val authTokens = accountAuthenticator
        .appAuth(appAuthPublicKey, tokenScope)
        .mapError { AccountCreationAuthError(it) }
        .bind()
        .authTokens

      authTokensService
        .setTokens(accountId, authTokens, tokenScope)
        .mapError { FailedToSaveAuthTokens(it) }
        .bind()
    }
  }
}
