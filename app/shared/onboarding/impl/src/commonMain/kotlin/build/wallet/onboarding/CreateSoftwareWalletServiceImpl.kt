package build.wallet.onboarding

import build.wallet.account.AccountService
import build.wallet.account.AccountStatus.NoAccount
import build.wallet.bitkey.account.OnboardingSoftwareAccount
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.bitkey.f8e.SoftwareKeyDefinitionId
import build.wallet.bitkey.keybox.SoftwareKeybox
import build.wallet.debug.DebugOptionsService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensure
import build.wallet.f8e.onboarding.frost.ActivateSpendingDescriptorF8eClient
import build.wallet.f8e.onboarding.frost.ContinueDistributedKeygenF8eClient
import build.wallet.f8e.onboarding.frost.InitiateDistributedKeygenF8eClient
import build.wallet.feature.flags.SoftwareWalletIsEnabledFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.frost.SealedRequest
import build.wallet.frost.ShareGeneratorFactory
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class CreateSoftwareWalletServiceImpl(
  private val accountService: AccountService,
  private val initiateDistributedKeygenF8eClient: InitiateDistributedKeygenF8eClient,
  private val continueDistributedKeygenF8eClient: ContinueDistributedKeygenF8eClient,
  private val activateSpendingDescriptorF8eClient: ActivateSpendingDescriptorF8eClient,
  private val softwareAccountCreator: SoftwareAccountCreator,
  private val appKeysGenerator: AppKeysGenerator,
  private val debugOptionsService: DebugOptionsService,
  private val softwareWalletIsEnabledFeatureFlag: SoftwareWalletIsEnabledFeatureFlag,
  private val shareGeneratorFactory: ShareGeneratorFactory,
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
      val shareGenerator = shareGeneratorFactory.createShareGenerator()
      // TODO add error handling
      val sealedRequest = shareGenerator.generate().result.value

      val response = initiateDistributedKeygenF8eClient.initiateDistributedKeygen(
        f8eEnvironment = account.config.f8eEnvironment,
        accountId = account.accountId,
        appAuthKey = account.appGlobalAuthKey,
        networkType = account.config.bitcoinNetworkType,
        sealedRequest = sealedRequest
      ).bind()

      val softwareKeyDefinitionId = SoftwareKeyDefinitionId(response.keyDefinitionId)

      val shareDetails = shareGenerator.aggregate(
        sealedRequest = SealedRequest(response.sealedResponse)
      ).result.value
      val continueSealedRequest = shareGenerator.encode(shareDetails).result.value

      continueDistributedKeygenF8eClient.continueDistributedKeygen(
        f8eEnvironment = account.config.f8eEnvironment,
        accountId = account.accountId,
        softwareKeyDefinitionId = softwareKeyDefinitionId,
        appAuthKey = account.appGlobalAuthKey,
        sealedRequest = continueSealedRequest
      ).bind()

      activateSpendingDescriptorF8eClient.activateSpendingKey(
        f8eEnvironment = account.config.f8eEnvironment,
        accountId = account.accountId,
        appAuthKey = account.appGlobalAuthKey,
        softwareKeyDefinitionId = softwareKeyDefinitionId
      ).bind()

      SoftwareKeybox(
        id = softwareKeyDefinitionId.value,
        networkType = account.config.bitcoinNetworkType,
        authKey = account.appGlobalAuthKey,
        recoveryAuthKey = account.recoveryAuthKey,
        shareDetails
      )
    }.logFailure { "Error creating keybox for software wallet account." }
  }
}
