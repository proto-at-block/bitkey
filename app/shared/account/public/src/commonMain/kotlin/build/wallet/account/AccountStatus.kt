package build.wallet.account

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount

/**
 * Describes current [Account] status.
 */
sealed interface AccountStatus {
  /**
   * There's an active (ready to use) [Account] - Full or Lite.
   */
  data class ActiveAccount(
    val account: Account,
  ) : AccountStatus

  /**
   *  There's an [Account] but it's currently in the process of finishing onboarding, before it
   *  can be activated.
   */
  data class OnboardingAccount(
    val account: Account,
  ) : AccountStatus

  /**
   * There is no [Account] data - "logged out" state.
   */
  data object NoAccount : AccountStatus

  /**
   * A [LiteAccount] is upgrading to become the given [FullAccount].
   *
   * We are only in this situation while the [FullAccount] is in the process of onboarding.
   * Once it completes onboarding, the [LiteAccount] is deleted and the [FullAccount] is activated.
   * In this state, the [LiteAccount] is no longer used / needed, but we haven't deleted it yet
   * so that we can manage the data state flow via [HasActiveLiteAccountDataStateMachine].
   */
  data class LiteAccountUpgradingToFullAccount(
    val account: FullAccount,
  ) : AccountStatus

  companion object {
    fun accountFromAccountStatus(accountStatus: AccountStatus): Account? {
      return when (accountStatus) {
        NoAccount -> null
        is ActiveAccount -> accountStatus.account
        is OnboardingAccount -> accountStatus.account
        is LiteAccountUpgradingToFullAccount -> accountStatus.account
      }
    }
  }
}
