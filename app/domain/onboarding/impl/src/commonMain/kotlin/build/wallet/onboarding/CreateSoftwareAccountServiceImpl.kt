package build.wallet.onboarding

import bitkey.account.AccountConfigService
import bitkey.auth.AuthTokenScope
import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AuthTokensService
import build.wallet.bitkey.account.OnboardingSoftwareAccount
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.f8e.SoftwareAccountId
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.onboarding.CreateSoftwareAccountF8eClient
import build.wallet.onboarding.SoftwareAccountCreationError.SoftwareAccountCreationAuthError
import build.wallet.onboarding.SoftwareAccountCreationError.SoftwareAccountCreationDatabaseError.FailedToSaveAuthTokens
import build.wallet.onboarding.SoftwareAccountCreationError.SoftwareAccountCreationF8eError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError

@BitkeyInject(AppScope::class)
class CreateSoftwareAccountServiceImpl(
  private val createSoftwareAccountF8eClient: CreateSoftwareAccountF8eClient,
  private val accountAuthenticator: AccountAuthenticator,
  private val authTokensService: AuthTokensService,
  private val accountConfigService: AccountConfigService,
) : CreateSoftwareAccountService {
  override suspend fun createAccount(
    authKey: PublicKey<AppGlobalAuthKey>,
    recoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
  ): Result<OnboardingSoftwareAccount, SoftwareAccountCreationError> =
    coroutineBinding {
      val config = accountConfigService.defaultConfig().value.toSoftwareAccountConfig()
      // Create a new account on the server and get a server key back.
      val accountServerResponse =
        createSoftwareAccountF8eClient
          .createAccount(
            authKey = authKey,
            recoveryAuthKey = recoveryAuthKey,
            accountConfig = config
          )
          .mapError { SoftwareAccountCreationF8eError(it) }
          .bind()
      val customerAccountId = SoftwareAccountId(accountServerResponse.serverId)

      // Store the [Global] scope auth tokens
      authenticateWithF8eAndStoreAuthTokens(
        accountId = customerAccountId,
        appAuthPublicKey = authKey,
        tokenScope = AuthTokenScope.Global
      ).bind()

      // Store the [Recovery] scope auth tokens
      authenticateWithF8eAndStoreAuthTokens(
        accountId = customerAccountId,
        appAuthPublicKey = recoveryAuthKey,
        tokenScope = AuthTokenScope.Recovery
      ).bind()

      // TODO: Add device token using the device token manager.
      // TODO: use notificationTouchpointF8eClient to get touchpoints.

      OnboardingSoftwareAccount(
        accountId = customerAccountId,
        config = config,
        appGlobalAuthKey = authKey,
        recoveryAuthKey = recoveryAuthKey
      )
    }

  /**
   * Performs auth with f8e using the given [AppAuthPublicKey] and stores the resulting
   * tokens in [AuthTokenDao] keyed by the given [AuthTokenScope]
   */
  private suspend fun authenticateWithF8eAndStoreAuthTokens(
    accountId: SoftwareAccountId,
    appAuthPublicKey: PublicKey<out AppAuthKey>,
    tokenScope: AuthTokenScope,
  ): Result<Unit, SoftwareAccountCreationError> =
    coroutineBinding {
      val authTokens =
        accountAuthenticator
          .appAuth(
            appAuthPublicKey = appAuthPublicKey,
            authTokenScope = tokenScope
          ).mapError { SoftwareAccountCreationAuthError(it) }
          .bind()
          .authTokens

      authTokensService
        .setTokens(accountId, authTokens, tokenScope)
        .mapError { FailedToSaveAuthTokens(it) }
        .bind()
    }
}
