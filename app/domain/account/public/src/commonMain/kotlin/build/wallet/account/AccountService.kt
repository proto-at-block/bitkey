package build.wallet.account

import build.wallet.bitkey.account.Account
import build.wallet.ensure
import build.wallet.ensureNotNull
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Domain service for managing account state.
 *
 * Allows to access current [AccountStatus] (active, onboarding, or no account).
 * Also, allows to activate an account (for example as part of recovery or onboarding).
 */
interface AccountService {
  /**
   * Returns current Account status:
   * - there's an active account (Full or Lite)
   * - there's an onboarding account (Full or Lite)
   * - there's no account status ("logged out" state)
   */
  fun accountStatus(): Flow<Result<AccountStatus, Error>>

  /**
   * Emits currently active account, if any.
   */
  fun activeAccount(): Flow<Account?>

  suspend fun setActiveAccount(account: Account): Result<Unit, Error>

  /**
   * Saves the given account to the db and sets it as the onboarding account.
   */
  suspend fun saveAccountAndBeginOnboarding(account: Account): Result<Unit, Error>

  suspend fun clear(): Result<Unit, Error>
}

/**
 * Returns current active account of type [AccountT], if present.
 * If no active account found, returns null.
 * If active account is not of type [AccountT], returns an error.
 */
suspend inline fun <reified AccountT : Account> AccountService.getAccountOrNull(): Result<AccountT?, Error> =
  coroutineBinding {
    val account = activeAccount().first()
    when (account) {
      null -> null
      else -> {
        ensure(account is AccountT) {
          Error("No active ${AccountT::class.simpleName} present, found ${account::class.simpleName}.")
        }
        account
      }
    }
  }

/**
 * Returns account of type [AccountT], if one is active or onboarding.
 * If no active or onboarding account found, returns null.
 * If a lite account is upgrading to a full account, returns the full account.
 * If active or onboarding account is not of type [AccountT], returns an error.
 */
suspend inline fun <reified AccountT : Account> AccountService.getActiveOrOnboardingAccountOrNull(): Result<AccountT?, Error> =
  coroutineBinding {
    val accountStatus = accountStatus().first().get()
    val account = when (val status = accountStatus) {
      is AccountStatus.ActiveAccount -> status.account
      is AccountStatus.LiteAccountUpgradingToFullAccount -> status.onboardingAccount
      is AccountStatus.OnboardingAccount -> status.account
      AccountStatus.NoAccount, null -> null
    }

    when (account) {
      null -> null
      else -> {
        ensure(account is AccountT) {
          Error("No active or onboarding ${AccountT::class.simpleName} present, found ${account::class.simpleName}.")
        }
        account
      }
    }
  }

/**
 * Returns current active account of type [AccountT].
 * If no active account found or active account is not of type [AccountT], returns an error.
 */
suspend inline fun <reified AccountT : Account> AccountService.getAccount(): Result<AccountT, Error> =
  coroutineBinding {
    val account = getAccountOrNull<AccountT>().bind()
    ensureNotNull(account) {
      Error("No active ${AccountT::class.simpleName} present, found none.")
    }
    account
  }
