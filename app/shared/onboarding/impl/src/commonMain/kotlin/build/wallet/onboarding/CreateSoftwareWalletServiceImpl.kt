package build.wallet.onboarding

import build.wallet.account.AccountRepository
import build.wallet.account.AccountStatus.NoAccount
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.OnboardingSoftwareAccount
import build.wallet.bitkey.account.SoftwareAccountConfig
import build.wallet.ensure
import build.wallet.f8e.onboarding.frost.ActivateSpendingDescriptorF8eClient
import build.wallet.f8e.onboarding.frost.ContinueDistributedKeygenF8eClient
import build.wallet.f8e.onboarding.frost.InitiateDistributedKeygenF8eClient
import build.wallet.feature.flags.SoftwareWalletIsEnabledFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.keybox.config.TemplateFullAccountConfigDao
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.logging.logFailure
import build.wallet.nfc.FakeHardwareKeyStore
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.first

class CreateSoftwareWalletServiceImpl(
  private val accountRepository: AccountRepository,
  private val initiateDistributedKeygenF8eClient: InitiateDistributedKeygenF8eClient,
  private val continueDistributedKeygenF8eClient: ContinueDistributedKeygenF8eClient,
  private val activateSpendingDescriptorF8eClient: ActivateSpendingDescriptorF8eClient,
  private val fakeHardwareKeyStore: FakeHardwareKeyStore,
  private val softwareAccountCreator: SoftwareAccountCreator,
  private val appKeysGenerator: AppKeysGenerator,
  private val templateFullAccountConfigDao: TemplateFullAccountConfigDao,
  private val softwareWalletIsEnabledFeatureFlag: SoftwareWalletIsEnabledFeatureFlag,
) : CreateSoftwareWalletService {
  override suspend fun createAccount(): Result<Account, Throwable> {
    return coroutineBinding {
      val softwareWalletFeatureEnabled = softwareWalletIsEnabledFeatureFlag.isEnabled()
      ensure(softwareWalletFeatureEnabled) {
        Error("Software wallet feature flag is not enabled.")
      }

      // TODO(W-8965) make software account onboarding resumable
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

      createKeybox(account).bind()

      // TODO(W-8964) Transition from onboardingSoftwareAccount to activatedSoftwareAccount

      account
    }.logFailure { "Error creating software wallet account with fake hw keys." }
  }

  private suspend fun createKeybox(account: OnboardingSoftwareAccount): Result<Unit, Throwable> {
    return coroutineBinding {
      val hwAuthKey = fakeHardwareKeyStore.getAuthKeypair().publicKey

      val keysetId = initiateDistributedKeygenF8eClient.initiateDistributedKeygen(
        f8eEnvironment = account.config.f8eEnvironment,
        accountId = account.accountId,
        appAuthKey = account.appGlobalAuthKey,
        networkType = account.config.bitcoinNetworkType,
        hwAuthKey = hwAuthKey
      ).bind()

      continueDistributedKeygenF8eClient.continueDistributedKeygen(
        f8eEnvironment = account.config.f8eEnvironment,
        accountId = account.accountId,
        keysetId = keysetId,
        appAuthKey = account.appGlobalAuthKey
      ).bind()

      activateSpendingDescriptorF8eClient.activateSpendingDescriptor(
        f8eEnvironment = account.config.f8eEnvironment,
        accountId = account.accountId,
        appAuthKey = account.appGlobalAuthKey,
        keysetId = keysetId
      ).bind()

      // TODO(W-8963) Create a keybox/icebox
      // TODO(W-8963) Save the keybox/icebox to database
    }.logFailure { "Error creating keybox for software wallet account." }
  }

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
