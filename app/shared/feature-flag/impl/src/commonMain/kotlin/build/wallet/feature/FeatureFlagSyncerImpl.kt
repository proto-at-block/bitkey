package build.wallet.feature

import build.wallet.account.AccountRepository
import build.wallet.account.AccountStatus
import build.wallet.bitkey.account.Account
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.featureflags.F8eFeatureFlagValue
import build.wallet.f8e.featureflags.GetFeatureFlagsService
import build.wallet.f8e.featureflags.GetFeatureFlagsService.F8eFeatureFlag
import build.wallet.isOk
import build.wallet.keybox.config.TemplateFullAccountConfigDao
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.logging.logNetworkFailure
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.first

class FeatureFlagSyncerImpl(
  private val accountRepository: AccountRepository,
  private val templateFullAccountConfigDao: TemplateFullAccountConfigDao,
  private val getFeatureFlagsService: GetFeatureFlagsService,
  private val booleanFlags: List<FeatureFlag<FeatureFlagValue.BooleanFlag>>,
) : FeatureFlagSyncer {
  override suspend fun sync() {
    val account = accountRepository.accountStatus().first().get()?.let {
      when (it) {
        is AccountStatus.ActiveAccount -> it.account
        is AccountStatus.LiteAccountUpgradingToFullAccount -> it.account
        AccountStatus.NoAccount -> null
        is AccountStatus.OnboardingAccount -> it.account
      }
    }

    val accountId = account?.accountId

    val f8eEnvironment = account.getF8eEnvironment()
    if (f8eEnvironment == null) {
      log(LogLevel.Error) { "Failed to get f8eEnvironment, feature flags will not sync" }
      return
    }

    getFeatureFlagsService.getF8eFeatureFlags(
      f8eEnvironment = f8eEnvironment,
      accountId = accountId,
      flagKeys = booleanFlags.map { it.identifier }
    )
      .onSuccess { remoteFlags ->
        remoteFlags.forEach { remoteFlag ->
          updateLocalFlagValue(remoteFlag)
        }
      }
      .onFailure {
        // The app encountered a network error when fetching remote feature flags from f8e. By
        // design, do nothing in this case and leave the local flags as they were.
      }
      .logNetworkFailure { "Failed to get feature flags from f8e" }
  }

  private suspend fun updateLocalFlagValue(remoteFlag: F8eFeatureFlag) {
    when (val remoteFeatureFlagValue = remoteFlag.value) {
      is F8eFeatureFlagValue.BooleanValue -> {
        booleanFlags
          .filter { it.identifier == remoteFlag.key }
          .filter { !it.isOverridden() }
          .filter {
            it.canSetValue(FeatureFlagValue.BooleanFlag(remoteFeatureFlagValue.value)).isOk()
          }
          .forEach { it.setFlagValue(remoteFeatureFlagValue.value) }
      }

      is F8eFeatureFlagValue.DoubleValue -> TODO("W-5800 Implement feature flag support for double")
      is F8eFeatureFlagValue.StringValue -> TODO("W-5800 Implement feature flag support for string")
    }
  }

  private suspend fun Account?.getF8eEnvironment(): F8eEnvironment? {
    return this?.config?.f8eEnvironment
      ?: templateFullAccountConfigDao.config().first().get()?.f8eEnvironment
  }
}
