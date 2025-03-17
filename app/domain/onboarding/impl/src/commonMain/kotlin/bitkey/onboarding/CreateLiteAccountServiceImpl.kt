package bitkey.onboarding

import bitkey.account.LiteAccountConfig
import bitkey.auth.AuthTokenScope
import build.wallet.account.AccountService
import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AuthTokensService
import build.wallet.bitkey.account.LiteAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.onboarding.CreateLiteAccountF8eClient
import build.wallet.keybox.keys.AppKeysGenerator
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError

@BitkeyInject(AppScope::class)
class CreateLiteAccountServiceImpl(
  private val accountAuthenticator: AccountAuthenticator,
  private val accountService: AccountService,
  private val authTokensService: AuthTokensService,
  private val appKeysGenerator: AppKeysGenerator,
  private val createLiteAccountF8eClient: CreateLiteAccountF8eClient,
) : CreateLiteAccountService {
  override suspend fun createAccount(
    config: LiteAccountConfig,
  ): Result<LiteAccount, LiteAccountCreationError> =
    coroutineBinding {
      // Create keys
      val recoveryKey =
        appKeysGenerator.generateRecoveryAuthKey()
          .mapError { LiteAccountCreationError.LiteAccountKeyGenerationError(it) }
          .bind()

      // Create a new lite account on the server and get an account ID back.
      val accountId =
        createLiteAccountF8eClient
          .createAccount(
            recoveryKey = recoveryKey,
            config = config
          )
          .mapError { LiteAccountCreationError.LiteAccountCreationF8eError(it) }
          .bind()

      val account = LiteAccount(accountId, config, recoveryKey)

      // Authorize the account
      val authTokens =
        accountAuthenticator
          .appAuth(
            appAuthPublicKey = recoveryKey,
            authTokenScope = AuthTokenScope.Recovery
          )
          .mapError { LiteAccountCreationError.LiteAccountCreationAuthError(it) }
          .bind()
          .authTokens

      // Persist the account auth tokens
      authTokensService
        .setTokens(accountId, authTokens, AuthTokenScope.Recovery)
        .mapError {
          LiteAccountCreationError.LiteAccountCreationDatabaseError.FailedToSaveAuthTokens(
            it
          )
        }
        .bind()

      // Save the account and being onboarding
      accountService.saveAccountAndBeginOnboarding(account)
        .mapError { LiteAccountCreationError.LiteAccountCreationDatabaseError.FailedToSaveAccount(it) }
        .bind()

      // Return the lite account
      account
    }
}
