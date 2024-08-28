package build.wallet.bootstrap

import build.wallet.account.AccountRepository
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.auth.FullAccountAuthKeyRotationService
import build.wallet.bitkey.account.FullAccount
import build.wallet.feature.FeatureFlagService
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

class LoadAppServiceImpl(
  private val featureFlagService: FeatureFlagService,
  private val accountRepository: AccountRepository,
  private val fullAccountAuthKeyRotationService: FullAccountAuthKeyRotationService,
) : LoadAppService {
  override suspend fun loadAppState(): AppState {
    // Suspend logic for loading the app state until feature flags are initialized.
    // Feature flags are initialized by an app worker on app launch.
    suspendUntilFeatureFlagsInitialized()

    val accountStatus = accountRepository.accountStatus().first().get()
      ?: return AppState.Undetermined

    return when (accountStatus) {
      is ActiveAccount -> {
        when (val account = accountStatus.account) {
          is FullAccount -> {
            val pendingAuthKeyRotation = fullAccountAuthKeyRotationService
              .observePendingKeyRotationAttemptUntilNull()
              .first()
            AppState.HasActiveFullAccount(
              account = account,
              pendingAuthKeyRotation = pendingAuthKeyRotation
            )
          }
          else -> {
            AppState.Undetermined
          }
        }
      }
      else -> {
        AppState.Undetermined
      }
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
