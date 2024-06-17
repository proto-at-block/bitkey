package build.wallet.auth

import build.wallet.account.AccountRepository
import build.wallet.account.AccountStatus
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.appRecoveryAuthKey
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.f8e.AccountId
import build.wallet.crypto.PublicKey
import build.wallet.ensure
import build.wallet.f8e.F8eEnvironment
import build.wallet.recovery.RecoveryAppAuthPublicKeyProvider
import build.wallet.recovery.RecoveryAppAuthPublicKeyProviderError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.flow.first

class AppAuthPublicKeyProviderImpl(
  private val accountRepository: AccountRepository,
  private val recoveryAppAuthPublicKeyProvider: RecoveryAppAuthPublicKeyProvider,
) : AppAuthPublicKeyProvider {
  override suspend fun getAppAuthPublicKeyFromAccountOrRecovery(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    tokenScope: AuthTokenScope,
  ): Result<PublicKey<out AppAuthKey>, AuthError> {
    return coroutineBinding {
      // First, try to get the active or onboarding account
      val account =
        getAccount(f8eEnvironment, accountId)
          .bind()

      // If there is no account currently onboarding or active, try to find an active recovery
      when (account) {
        null ->
          recoveryAppAuthPublicKeyProvider.getAppPublicKeyForInProgressRecovery(scope = tokenScope)
            .mapError {
              when (it) {
                is RecoveryAppAuthPublicKeyProviderError.NoRecoveryInProgress ->
                  AccountMissing
                is RecoveryAppAuthPublicKeyProviderError.FailedToReadRecoveryEntity ->
                  FailedToReadRecoveryStatus(it)
                is RecoveryAppAuthPublicKeyProviderError.NoRecoveryAuthKey ->
                  AppRecoveryAuthPublicKeyMissing
              }
            }
            .bind()

        else ->
          account.appAuthPublicKey(tokenScope)
            .bind()
      }
    }
  }

  private suspend fun getAccount(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
  ): Result<Account?, AuthError> {
    return coroutineBinding {
      val accountStatus = accountRepository.accountStatus().first()
        .mapError { FailedToReadAccountStatus(it) }
        .bind()

      // Get the current account from the account status, or early return null if there is no account
      val account: Account? = when (accountStatus) {
        is AccountStatus.NoAccount -> null
        is AccountStatus.ActiveAccount -> accountStatus.account
        is AccountStatus.OnboardingAccount -> accountStatus.account
        is AccountStatus.LiteAccountUpgradingToFullAccount -> accountStatus.account
      }

      if (account != null) {
        // Check that the returned account matches the expected [F8eEnvironment] and [AccountId]
        ensure(account.config.f8eEnvironment == f8eEnvironment) {
          UnhandledError(
            IllegalStateException(
              "Current account f8e environment: ${account.config.f8eEnvironment}. Requested f8e environment: $f8eEnvironment "
            )
          )
        }

        ensure(account.accountId.serverId == accountId.serverId) {
          UnhandledError(
            IllegalStateException(
              "Current account ID: ${account.accountId.serverId}. Requested account ID: ${accountId.serverId}"
            )
          )
        }
      }

      // Return the account, if any
      account
    }
  }
}

/**
 * The [AppAuthPublicKey] for the Account that generates the given [AuthTokenScope].
 */
private fun Account.appAuthPublicKey(
  tokenScope: AuthTokenScope,
): Result<PublicKey<out AppAuthKey>, AuthError> {
  return when (this) {
    // Full accounts can generate either [Global] or [RecoveryApp] auth tokens.
    is FullAccount ->
      Ok(
        when (tokenScope) {
          AuthTokenScope.Global -> keybox.activeAppKeyBundle.authKey
          AuthTokenScope.Recovery -> appRecoveryAuthKey
        }
      )

    // Lite accounts can only generate [RecoveryApp] auth tokens.
    is LiteAccount ->
      when (tokenScope) {
        AuthTokenScope.Global -> Err(RequestGlobalScopeForLiteAccount)
        AuthTokenScope.Recovery -> Ok(recoveryAuthKey)
      }
  }
}
