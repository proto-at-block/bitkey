package build.wallet.onboarding

import build.wallet.account.AccountRepository
import build.wallet.account.AccountStatus.NoAccount
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.SoftwareAccountConfig
import build.wallet.ensure
import build.wallet.feature.flags.SoftwareWalletIsEnabledFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.keybox.config.TemplateFullAccountConfigDao
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.first

class CreateSoftwareWalletWorkflowImpl(
  private val accountRepository: AccountRepository,
  private val softwareAccountCreator: SoftwareAccountCreator,
  private val appKeysGenerator: AppKeysGenerator,
  private val templateFullAccountConfigDao: TemplateFullAccountConfigDao,
  private val softwareWalletIsEnabledFeatureFlag: SoftwareWalletIsEnabledFeatureFlag,
) : CreateSoftwareWalletWorkflow {
  override suspend fun createAccount(): Result<Account, Throwable> =
    coroutineBinding {
      val softwareWalletFeatureEnabled = softwareWalletIsEnabledFeatureFlag.isEnabled()
      ensure(softwareWalletFeatureEnabled) {
        Error("Software wallet feature flag is not enabled.")
      }

      val currentAccountStatus = accountRepository.accountStatus().first().bind()
      val noActiveOrOnboardingAccount = currentAccountStatus == NoAccount
      ensure(noActiveOrOnboardingAccount) {
        Error("Expected no active or onboarding account, but found: $currentAccountStatus.")
      }

      val config = templateAccountConfig()
      val newAppKeys = appKeysGenerator.generateKeyBundle(config.bitcoinNetworkType).bind()

      val account = softwareAccountCreator
        .createAccount(
          authKey = newAppKeys.authKey,
          recoveryAuthKey = newAppKeys.recoveryAuthKey,
          config = config
        ).bind()

      accountRepository
        .saveAccountAndBeginOnboarding(account)
        .bind()

      account
    }.logFailure { "Error creating software wallet account with fake hw keys." }

  /**
   * Returns a [SoftwareAccountConfig] to use for the new account.
   * Can be customized through debug menu.
   */
  private suspend fun templateAccountConfig(): SoftwareAccountConfig {
    // TODO(W-8716): implement component to manage template config for the Software Account.
    val fullAccountConfig = templateFullAccountConfigDao.config().first().value
    return SoftwareAccountConfig(
      bitcoinNetworkType = fullAccountConfig.bitcoinNetworkType,
      f8eEnvironment = fullAccountConfig.f8eEnvironment,
      isTestAccount = fullAccountConfig.isTestAccount,
      isUsingSocRecFakes = fullAccountConfig.isUsingSocRecFakes
    )
  }
}
