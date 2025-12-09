package build.wallet.bootstrap

import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.auth.FullAccountAuthKeyRotationService
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.OnboardingSoftwareAccount
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.bootstrap.AppState.*
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.FeatureFlagService
import com.github.michaelbull.result.getOrThrow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class LoadAppServiceImpl(
  private val featureFlagService: FeatureFlagService,
  private val accountService: AccountService,
  private val fullAccountAuthKeyRotationService: FullAccountAuthKeyRotationService,
) : LoadAppService {
  override suspend fun loadAppState(): AppState {
    // Suspend logic for loading the app state until feature flags are initialized.
    // Feature flags are initialized by an app worker on app launch.
    suspendUntilFeatureFlagsInitialized()

    return when (val accountStatus = accountService.accountStatus().first().getOrThrow()) {
      is ActiveAccount -> {
        when (val account = accountStatus.account) {
          is FullAccount -> {
            val pendingAuthKeyRotation = fullAccountAuthKeyRotationService
              .observePendingKeyRotationAttemptUntilNull()
              .first()
            HasActiveFullAccount(
              account = account,
              pendingAuthKeyRotation = pendingAuthKeyRotation
            )
          }
          is SoftwareAccount -> {
            HasActiveSoftwareAccount(
              account = account
            )
          }
          is LiteAccount -> HasActiveLiteAccount(account = account)
          is OnboardingSoftwareAccount -> error("Onboarding software account is currently not handled")
        }
      }
      is AccountStatus.OnboardingAccount -> when (val account = accountStatus.account) {
        is FullAccount -> OnboardingFullAccount(account = account)
        is LiteAccount -> error("Onboarding lite account is currently not handled")
        is OnboardingSoftwareAccount -> error("Onboarding software account is currently not handled")
        is SoftwareAccount -> error("Onboarding software account is currently not handled")
      }
      is AccountStatus.LiteAccountUpgradingToFullAccount -> LiteAccountOnboardingToFullAccount(
        activeAccount = accountStatus.liteAccount,
        onboardingAccount = accountStatus.onboardingAccount
      )
      AccountStatus.NoAccount -> NoActiveAccount
    }
  }

  /**
   * Suspends until feature flags are initialized.
   */
  private suspend fun suspendUntilFeatureFlagsInitialized() {
    featureFlagService.flagsInitialized
      .filter { initialized -> initialized }
      .first()
  }
}
