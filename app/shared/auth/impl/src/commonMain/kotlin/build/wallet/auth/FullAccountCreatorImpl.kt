package build.wallet.auth

import build.wallet.auth.AccountCreationError.AccountCreationAuthError
import build.wallet.auth.AccountCreationError.AccountCreationDatabaseError.FailedToSaveAuthTokens
import build.wallet.auth.AccountCreationError.AccountCreationDatabaseError.FailedToSaveKeybox
import build.wallet.auth.AccountCreationError.AccountCreationF8eError
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.app.AppAuthPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeysAndHardwareKeys
import build.wallet.bitkey.keybox.Keybox
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.notifications.NotificationTouchpointService
import build.wallet.f8e.onboarding.CreateFullAccountService
import build.wallet.keybox.KeyboxDao
import build.wallet.keybox.builder.KeyCrossBuilder
import build.wallet.notifications.DeviceTokenManager
import build.wallet.notifications.NotificationTouchpointDao
import build.wallet.platform.random.Uuid
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onSuccess

class FullAccountCreatorImpl(
  private val keyCrossBuilder: KeyCrossBuilder,
  private val keyboxDao: KeyboxDao,
  private val createFullAccountService: CreateFullAccountService,
  private val accountAuthenticator: AccountAuthenticator,
  private val authTokenDao: AuthTokenDao,
  private val deviceTokenManager: DeviceTokenManager,
  private val uuid: Uuid,
  private val notificationTouchpointService: NotificationTouchpointService,
  private val notificationTouchpointDao: NotificationTouchpointDao,
) : FullAccountCreator {
  override suspend fun createAccount(
    keyCrossDraft: WithAppKeysAndHardwareKeys,
  ): Result<FullAccount, AccountCreationError> =
    binding {
      // Create a new account on the server and get a server key back.
      val accountServerResponse =
        createFullAccountService
          .createAccount(keyCrossDraft)
          .mapError { AccountCreationF8eError(it) }
          .bind()
      val customerAccountId = accountServerResponse.fullAccountId

      val keyCross =
        keyCrossBuilder
          .addServerKey(
            draft = keyCrossDraft,
            f8eSpendingKeyset = accountServerResponse.f8eSpendingKeyset
          )

      // Store the [Global] scope auth tokens
      authenticateWithF8eAndStoreAuthTokens(
        accountId = customerAccountId,
        appAuthPublicKey = keyCross.appKeyBundle.authKey,
        f8eEnvironment = keyCross.config.f8eEnvironment,
        tokenScope = AuthTokenScope.Global
      )

      // Store the [Recovery] scope auth tokens
      authenticateWithF8eAndStoreAuthTokens(
        accountId = customerAccountId,
        appAuthPublicKey = requireNotNull(keyCross.appKeyBundle.recoveryAuthKey),
        f8eEnvironment = keyCross.config.f8eEnvironment,
        tokenScope = AuthTokenScope.Recovery
      )

      // Don't bind the error so we don't block account creation on the success of adding
      // the device token because it won't yet be available on iOS until push notification
      // permissions are requested (which happens after account creation).
      deviceTokenManager
        .addDeviceTokenIfPresentForAccount(
          fullAccountId = customerAccountId,
          f8eEnvironment = keyCross.config.f8eEnvironment,
          authTokenScope = AuthTokenScope.Global
        )

      // We now have everything we need for our Keyset (app/hw/server spending keys)
      val keybox =
        Keybox(
          localId = uuid.random(),
          fullAccountId = customerAccountId,
          activeSpendingKeyset = keyCross.spendingKeyset,
          activeKeyBundle = keyCross.appKeyBundle,
          inactiveKeysets = emptyImmutableList(),
          config = keyCross.config
        )

      // Get notification touchpoints in the event that this is an existing account
      // In the event of failure, just continue with account creation.
      // Errors will be logged where they occur.
      notificationTouchpointService.getTouchpoints(
        f8eEnvironment = keyCross.config.f8eEnvironment,
        fullAccountId = customerAccountId
      ).get()
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

      FullAccount(customerAccountId, FullAccountConfig.fromKeyboxConfig(keybox.config), keybox)
    }

  /**
   * Performs auth with f8e using the given [AppAuthPublicKey] and stores the resulting
   * tokens in [AuthTokenDao] keyed by the given [AuthTokenScope]
   */
  private suspend fun authenticateWithF8eAndStoreAuthTokens(
    accountId: FullAccountId,
    appAuthPublicKey: AppAuthPublicKey,
    f8eEnvironment: F8eEnvironment,
    tokenScope: AuthTokenScope,
  ): Result<Unit, AccountCreationError> {
    return binding {
      val authTokens =
        accountAuthenticator
          .appAuth(
            f8eEnvironment = f8eEnvironment,
            appAuthPublicKey = appAuthPublicKey
          )
          .mapError { AccountCreationAuthError(it) }
          .bind()
          .authTokens

      authTokenDao
        .setTokensOfScope(accountId, authTokens, tokenScope)
        .mapError { FailedToSaveAuthTokens(it) }
        .bind()
    }
  }
}
