package build.wallet.onboarding

import build.wallet.account.AccountService
import build.wallet.account.AccountStatus.NoAccount
import build.wallet.bitkey.account.OnboardingSoftwareAccount
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.bitkey.keybox.SoftwareKeybox
import build.wallet.debug.DebugOptionsService
import build.wallet.ensure
import build.wallet.f8e.onboarding.frost.ActivateSpendingDescriptorF8eClient
import build.wallet.f8e.onboarding.frost.ContinueDistributedKeygenF8eClient
import build.wallet.f8e.onboarding.frost.InitiateDistributedKeygenF8eClient
import build.wallet.feature.flags.SoftwareWalletIsEnabledFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.logging.logFailure
import build.wallet.nfc.FakeHardwareKeyStore
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.first

class CreateSoftwareWalletServiceImpl(
  private val accountService: AccountService,
  private val initiateDistributedKeygenF8eClient: InitiateDistributedKeygenF8eClient,
  private val continueDistributedKeygenF8eClient: ContinueDistributedKeygenF8eClient,
  private val activateSpendingDescriptorF8eClient: ActivateSpendingDescriptorF8eClient,
  private val fakeHardwareKeyStore: FakeHardwareKeyStore,
  private val softwareAccountCreator: SoftwareAccountCreator,
  private val appKeysGenerator: AppKeysGenerator,
  private val debugOptionsService: DebugOptionsService,
  private val softwareWalletIsEnabledFeatureFlag: SoftwareWalletIsEnabledFeatureFlag,
) : CreateSoftwareWalletService {
  override suspend fun createAccount(): Result<SoftwareAccount, Throwable> {
    return coroutineBinding {
      val softwareWalletFeatureEnabled = softwareWalletIsEnabledFeatureFlag.isEnabled()
      ensure(softwareWalletFeatureEnabled) {
        Error("Software wallet feature flag is not enabled.")
      }

      // TODO(W-8965) make software account onboarding resumable
      val currentAccountStatus = accountService.accountStatus().first().bind()
      val noActiveOrOnboardingAccount = currentAccountStatus == NoAccount
      ensure(noActiveOrOnboardingAccount) {
        Error("Expected no active or onboarding account, but found: $currentAccountStatus.")
      }

      val config = debugOptionsService.options().first().toSoftwareAccountConfig()
      val newAppKeys = appKeysGenerator.generateKeyBundle(config.bitcoinNetworkType).bind()

      val onboardingAccount = softwareAccountCreator
        .createAccount(
          authKey = newAppKeys.authKey,
          recoveryAuthKey = newAppKeys.recoveryAuthKey,
          config = config
        ).bind()

      accountService
        .saveAccountAndBeginOnboarding(onboardingAccount)
        .bind()

      val keybox = createKeybox(onboardingAccount).bind()

      val activeAccount = SoftwareAccount(
        accountId = onboardingAccount.accountId,
        config = onboardingAccount.config,
        keybox = keybox
      )
      accountService.setActiveAccount(activeAccount)

      activeAccount
    }.logFailure { "Error creating software wallet account with fake hw keys." }
  }

  private suspend fun createKeybox(
    account: OnboardingSoftwareAccount,
  ): Result<SoftwareKeybox, Throwable> {
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

      SoftwareKeybox(
        id = keysetId.keysetId,
        authKey = account.appGlobalAuthKey,
        recoveryAuthKey = account.recoveryAuthKey
      )
    }.logFailure { "Error creating keybox for software wallet account." }
  }
}
