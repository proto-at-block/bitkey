package build.wallet.onboarding

import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.auth.AccountCreationError
import build.wallet.auth.FullAccountCreator
import build.wallet.auth.LiteToFullAccountUpgrader
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeys
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeysAndHardwareKeys
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.debug.DebugOptionsService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.CreateAccountClientErrorCode
import build.wallet.f8e.error.code.CreateAccountClientErrorCode.APP_AUTH_PUBKEY_IN_USE
import build.wallet.f8e.error.code.CreateAccountClientErrorCode.HW_AUTH_PUBKEY_IN_USE
import build.wallet.f8e.onboarding.OnboardingF8eClient
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTask.TaskId
import build.wallet.home.GettingStartedTask.TaskState
import build.wallet.home.GettingStartedTaskDao
import build.wallet.keybox.KeyboxDao
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.keybox.keys.OnboardingAppKeyKeystore
import build.wallet.logging.*
import build.wallet.logging.logFailure
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrolled
import build.wallet.onboarding.CreateFullAccountContext.LiteToFullAccountUpgrade
import build.wallet.onboarding.CreateFullAccountContext.NewFullAccount
import build.wallet.platform.random.UuidGenerator
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class CreateFullAccountServiceImpl(
  private val appKeysGenerator: AppKeysGenerator,
  private val debugOptionsService: DebugOptionsService,
  private val keyboxDao: KeyboxDao,
  private val onboardingAppKeyKeystore: OnboardingAppKeyKeystore,
  private val uuidGenerator: UuidGenerator,
  private val fullAccountCreator: FullAccountCreator,
  private val liteToFullAccountUpgrader: LiteToFullAccountUpgrader,
  private val onboardingKeyboxSealedCsekDao: OnboardingKeyboxSealedCsekDao,
  private val onboardingKeyboxHardwareKeysDao: OnboardingKeyboxHardwareKeysDao,
  private val onboardingF8eClient: OnboardingF8eClient,
  private val gettingStartedTaskDao: GettingStartedTaskDao,
  private val eventTracker: EventTracker,
  private val onboardingKeyboxStepStateDao: OnboardingKeyboxStepStateDao,
) : CreateFullAccountService {
  override suspend fun createAppKeys(): Result<WithAppKeys, Throwable> =
    coroutineBinding {
      val onboardingKeybox = keyboxDao.onboardingKeybox().first().bind()
      // Reuse keybox configuration used for ongoing onboarding,
      // otherwise fall back on debug options
      val accountConfig = onboardingKeybox?.config
        ?: debugOptionsService.options().first().toFullAccountConfig()

      val appKeyBundle = generateAppKeys(accountConfig.bitcoinNetworkType).bind()
      // once we successfully generate app keys, persist them in the case onboarding is
      // interrupted so we can resume account creation with the same keys
      onboardingAppKeyKeystore
        .persistAppKeys(
          spendingKey = appKeyBundle.spendingKey,
          globalAuthKey = appKeyBundle.authKey,
          recoveryAuthKey = appKeyBundle.recoveryAuthKey,
          bitcoinNetworkType = accountConfig.bitcoinNetworkType
        )
      WithAppKeys(appKeyBundle = appKeyBundle, config = accountConfig)
    }.logFailure { "Error generating app keys for a new full account." }

  private suspend fun generateAppKeys(
    networkType: BitcoinNetworkType,
  ): Result<AppKeyBundle, Throwable> =
    coroutineBinding {
      // if we have previously persisted app keys we restore those and return them (to keep account
      // creation idempotent). Otherwise, we create a new app key bundle for account creation.
      val appKeys = onboardingAppKeyKeystore.getAppKeyBundle(
        localId = uuidGenerator.random(),
        network = networkType
      )
      when (appKeys) {
        null -> {
          appKeysGenerator.generateKeyBundle(networkType).bind()
        }

        else -> {
          logDebug { "Using existing app key bundle" }
          appKeys
        }
      }
    }

  override suspend fun createAccount(
    context: CreateFullAccountContext,
    appKeys: WithAppKeys,
    hwActivation: FingerprintEnrolled,
  ): Result<FullAccount, Throwable> =
    coroutineBinding {
      storeSealedCsek(
        sealedCsek = hwActivation.sealedCsek,
        hwAuthKey = hwActivation.keyBundle.authKey,
        appGlobalAuthKeyHwSignature = hwActivation.appGlobalAuthKeyHwSignature
      ).bind()

      val appAndHwKeys = WithAppKeysAndHardwareKeys(
        appKeyBundle = appKeys.appKeyBundle,
        hardwareKeyBundle = hwActivation.keyBundle,
        appGlobalAuthKeyHwSignature = hwActivation.appGlobalAuthKeyHwSignature,
        config = appKeys.config
      )

      when (context) {
        is NewFullAccount -> fullAccountCreator.createAccount(appAndHwKeys).bind()
        is LiteToFullAccountUpgrade -> liteToFullAccountUpgrader.upgradeAccount(
          liteAccount = context.liteAccount,
          keyCrossDraft = appAndHwKeys
        ).bind()
      }
    }
      .mapError {
        when (it.createAccountClientErrorCode()) {
          HW_AUTH_PUBKEY_IN_USE -> HardwareKeyAlreadyInUseError(it)
          APP_AUTH_PUBKEY_IN_USE -> {
            // When the error is that the app key is already tied to an account, clear
            // the key and generate new keys before showing the customer error messaging
            // asking them to retry
            onboardingAppKeyKeystore.clear()
              .onFailure { error -> return@mapError error }
            createAppKeys().fold(
              success = { AppKeyAlreadyInUseError },
              failure = { error -> error }
            )
          }

          else -> it
        }
      }.logFailure { "Error creating full account on f8e." }

  private suspend fun storeSealedCsek(
    sealedCsek: SealedCsek,
    hwAuthKey: HwAuthPublicKey,
    appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      // Set the sealed CSEK so we have it available once the keybox is created
      // and the data state machine transitions
      onboardingKeyboxSealedCsekDao.set(sealedCsek).bind()
      // Save the hw auth public key in case we find a lite account backup and need to
      // go through lite => full account upgrade instead. Saving this pub key will allow
      // us to save a tap later.
      onboardingKeyboxHardwareKeysDao
        .set(
          OnboardingKeyboxHardwareKeys(
            hwAuthPublicKey = hwAuthKey,
            appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature
          )
        )
        .bind()
    }
      .mapError(::ErrorStoringSealedCsekError)
      .logFailure { "Error storing sealed CSEK when creating a full account." }

  override suspend fun activateAccount(keybox: Keybox): Result<Unit, Throwable> =
    coroutineBinding {
      // clear the app keys persisted specifically for onboarding upon completion since the account
      // and keybox are fully created
      onboardingAppKeyKeystore.clear().bind()

      // clear the hw auth public key that was stored for upgrading a lite account backup
      onboardingKeyboxHardwareKeysDao.clear()

      // Tell the server that onboarding has been completed.
      onboardingF8eClient
        .completeOnboarding(
          f8eEnvironment = keybox.config.f8eEnvironment,
          fullAccountId = keybox.fullAccountId
        )
        .bind()

      // Add getting started tasks for the new keybox
      val gettingStartedTasks = listOf(
        GettingStartedTask(TaskId.AddBitcoin, TaskState.Incomplete),
        GettingStartedTask(TaskId.InviteTrustedContact, TaskState.Incomplete),
        GettingStartedTask(TaskId.EnableSpendingLimit, TaskState.Incomplete),
        GettingStartedTask(TaskId.AddAdditionalFingerprint, TaskState.Incomplete)
      )

      gettingStartedTaskDao
        .addTasks(gettingStartedTasks)
        .logFailure { "Failed to add getting started tasks $gettingStartedTasks" }
        .bind()

      eventTracker.track(Action.ACTION_APP_GETTINGSTARTED_INITIATED)

      // Log that the account has been created
      eventTracker.track(Action.ACTION_APP_ACCOUNT_CREATED)

      // Set as active. This will transition the UI.
      keyboxDao
        .activateNewKeyboxAndCompleteOnboarding(keybox)
        .bind()

      // Now that we have an active keybox we can clear the temporary onboarding dao
      onboardingKeyboxStepStateDao.clear().bind()
    }

  override suspend fun cancelAccountCreation(): Result<Unit, Throwable> =
    coroutineBinding {
      onboardingAppKeyKeystore.clear().bind()
      onboardingKeyboxHardwareKeysDao.clear()
      onboardingKeyboxStepStateDao.clear().bind()
    }
}

private fun Throwable.createAccountClientErrorCode(): CreateAccountClientErrorCode? {
  val f8eError = this as? AccountCreationError.AccountCreationF8eError
  val clientError = f8eError?.f8eError as? F8eError.SpecificClientError
  return clientError?.errorCode
}
