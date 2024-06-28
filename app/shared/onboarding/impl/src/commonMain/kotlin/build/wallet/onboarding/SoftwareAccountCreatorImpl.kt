package build.wallet.onboarding

import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AuthTokenDao
import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.account.OnboardingSoftwareAccount
import build.wallet.bitkey.account.SoftwareAccountConfig
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.f8e.SoftwareAccountId
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.onboarding.CreateSoftwareAccountF8eClient
import build.wallet.onboarding.SoftwareAccountCreationError.SoftwareAccountCreationAuthError
import build.wallet.onboarding.SoftwareAccountCreationError.SoftwareAccountCreationDatabaseError.FailedToSaveAuthTokens
import build.wallet.onboarding.SoftwareAccountCreationError.SoftwareAccountCreationF8eError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError

class SoftwareAccountCreatorImpl(
  private val createSoftwareAccountF8eClient: CreateSoftwareAccountF8eClient,
  private val accountAuthenticator: AccountAuthenticator,
  private val authTokenDao: AuthTokenDao,
) : SoftwareAccountCreator {
  override suspend fun createAccount(
    authKey: PublicKey<AppGlobalAuthKey>,
    recoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
    config: SoftwareAccountConfig,
  ): Result<OnboardingSoftwareAccount, SoftwareAccountCreationError> =
    coroutineBinding {
      // Create a new account on the server and get a server key back.
      val accountServerResponse =
        createSoftwareAccountF8eClient
          .createAccount(authKey = authKey, recoveryAuthKey = recoveryAuthKey, accountConfig = config)
          .mapError { SoftwareAccountCreationF8eError(it) }
          .bind()
      val customerAccountId = SoftwareAccountId(accountServerResponse.serverId)

      // Store the [Global] scope auth tokens
      authenticateWithF8eAndStoreAuthTokens(
        accountId = customerAccountId,
        appAuthPublicKey = authKey,
        f8eEnvironment = config.f8eEnvironment,
        tokenScope = AuthTokenScope.Global
      ).bind()

      // Store the [Recovery] scope auth tokens
      authenticateWithF8eAndStoreAuthTokens(
        accountId = customerAccountId,
        appAuthPublicKey = recoveryAuthKey,
        f8eEnvironment = config.f8eEnvironment,
        tokenScope = AuthTokenScope.Recovery
      ).bind()

      // TODO: Add device token using the device token manager.
      // TODO: use notificationTouchpointF8eClient to get touchpoints.

      val account = OnboardingSoftwareAccount(
        accountId = customerAccountId,
        config = config,
        appGlobalAuthKey = authKey,
        recoveryAuthKey = recoveryAuthKey
      )

      // TODO: Save account to repository
      account
    }

  /**
   * Performs auth with f8e using the given [AppAuthPublicKey] and stores the resulting
   * tokens in [AuthTokenDao] keyed by the given [AuthTokenScope]
   */
  private suspend fun authenticateWithF8eAndStoreAuthTokens(
    accountId: SoftwareAccountId,
    appAuthPublicKey: PublicKey<out AppAuthKey>,
    f8eEnvironment: F8eEnvironment,
    tokenScope: AuthTokenScope,
  ): Result<Unit, SoftwareAccountCreationError> {
    return coroutineBinding {
      val authTokens =
        accountAuthenticator
          .appAuth(
            f8eEnvironment = f8eEnvironment,
            appAuthPublicKey = appAuthPublicKey,
            authTokenScope = tokenScope
          )
          .mapError { SoftwareAccountCreationAuthError(it) }
          .bind()
          .authTokens

      authTokenDao
        .setTokensOfScope(accountId, authTokens, tokenScope)
        .mapError { FailedToSaveAuthTokens(it) }
        .bind()
    }
  }
}
