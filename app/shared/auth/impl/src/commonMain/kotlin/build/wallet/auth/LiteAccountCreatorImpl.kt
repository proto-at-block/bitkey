package build.wallet.auth

import build.wallet.account.AccountRepository
import build.wallet.auth.LiteAccountCreationError.LiteAccountCreationAuthError
import build.wallet.auth.LiteAccountCreationError.LiteAccountCreationDatabaseError.FailedToSaveAccount
import build.wallet.auth.LiteAccountCreationError.LiteAccountCreationDatabaseError.FailedToSaveAuthTokens
import build.wallet.auth.LiteAccountCreationError.LiteAccountCreationF8eError
import build.wallet.auth.LiteAccountCreationError.LiteAccountKeyGenerationError
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.LiteAccountConfig
import build.wallet.f8e.onboarding.CreateLiteAccountService
import build.wallet.keybox.keys.AppKeysGenerator
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError

class LiteAccountCreatorImpl(
  private val accountAuthenticator: AccountAuthenticator,
  private val accountRepository: AccountRepository,
  private val authTokenDao: AuthTokenDao,
  private val appKeysGenerator: AppKeysGenerator,
  private val createLiteAccountService: CreateLiteAccountService,
) : LiteAccountCreator {
  override suspend fun createAccount(
    config: LiteAccountConfig,
  ): Result<LiteAccount, LiteAccountCreationError> =
    binding {
      // Create keys
      val recoveryKey =
        appKeysGenerator.generateRecoveryAuthKey()
          .mapError { LiteAccountKeyGenerationError(it) }
          .bind()

      // Create a new lite account on the server and get an account ID back.
      val accountId =
        createLiteAccountService
          .createAccount(
            recoveryKey = recoveryKey,
            config = config
          )
          .mapError { LiteAccountCreationF8eError(it) }
          .bind()

      val account = LiteAccount(accountId, config, recoveryKey)

      // Authorize the account
      val authTokens =
        accountAuthenticator
          .appAuth(
            f8eEnvironment = config.f8eEnvironment,
            appAuthPublicKey = recoveryKey,
            authTokenScope = AuthTokenScope.Recovery
          )
          .mapError { LiteAccountCreationAuthError(it) }
          .bind()
          .authTokens

      // Persist the account auth tokens
      authTokenDao
        .setTokensOfScope(accountId, authTokens, AuthTokenScope.Recovery)
        .mapError { FailedToSaveAuthTokens(it) }
        .bind()

      // Save the account and being onboarding
      accountRepository.saveAccountAndBeginOnboarding(account)
        .mapError { FailedToSaveAccount(it) }
        .bind()

      // Return the lite account
      account
    }
}
