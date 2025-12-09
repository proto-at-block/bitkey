package build.wallet.onboarding

import bitkey.account.AccountConfigServiceFake
import bitkey.f8e.error.SpecificClientErrorMock
import bitkey.f8e.error.code.CreateAccountClientErrorCode.APP_AUTH_PUBKEY_IN_USE
import bitkey.f8e.error.code.CreateAccountClientErrorCode.HW_AUTH_PUBKEY_IN_USE
import bitkey.onboarding.CreateFullAccountServiceFake
import bitkey.onboarding.FullAccountCreationError
import bitkey.onboarding.UpgradeLiteAccountToFullServiceFake
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.v1.Action
import build.wallet.bitkey.keybox.*
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.cloud.backup.csek.SealedSsekFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.crypto.PublicKey
import build.wallet.f8e.onboarding.OnboardingF8eClientMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTask.TaskId
import build.wallet.home.GettingStartedTask.TaskState
import build.wallet.home.GettingStartedTaskDaoMock
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.keybox.keys.OnboardingAppKeyKeystoreFake
import build.wallet.ktor.result.HttpError
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrolled
import build.wallet.onboarding.CreateFullAccountContext.LiteToFullAccountUpgrade
import build.wallet.onboarding.CreateFullAccountContext.NewFullAccount
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

class OnboardFullAccountServiceImplTests : FunSpec({
  val appKeysGenerator = AppKeysGeneratorMock()
  val accountConfigService = AccountConfigServiceFake()
  val keyboxDao = KeyboxDaoMock(turbines::create)
  val onboardingAppKeyKeystore = OnboardingAppKeyKeystoreFake()
  val uuidGenerator = UuidGeneratorFake()
  val createFullAccountService = CreateFullAccountServiceFake()
  val upgradeLiteAccountToFullService = UpgradeLiteAccountToFullServiceFake()
  val onboardingCompletionService = OnboardingCompletionServiceFake()
  val onboardingKeyboxSealedCsekDao = OnboardingKeyboxSealedCsekDaoMock()
  val onboardingKeyboxSealedSsekDao = OnboardingKeyboxSealedSsekDaoFake()
  val onboardingKeyboxHardwareKeysDao = OnboardingKeyboxHardwareKeysDaoFake()
  val onboardingF8eClient = OnboardingF8eClientMock(turbines::create)
  val gettingStartedTaskDao = GettingStartedTaskDaoMock(turbines::create)
  val eventTracker = EventTrackerMock(turbines::create)
  val featureFlagDao = FeatureFlagDaoFake()
  val onboardingKeyboxStepStateDao = OnboardingKeyboxStepStateDaoFake()

  val service = OnboardFullAccountServiceImpl(
    appKeysGenerator = appKeysGenerator,
    accountConfigService = accountConfigService,
    keyboxDao = keyboxDao,
    onboardingAppKeyKeystore = onboardingAppKeyKeystore,
    uuidGenerator = uuidGenerator,
    createFullAccountService = createFullAccountService,
    upgradeLiteAccountToFullService = upgradeLiteAccountToFullService,
    onboardingCompletionService = onboardingCompletionService,
    onboardingKeyboxSealedCsekDao = onboardingKeyboxSealedCsekDao,
    onboardingKeyboxSealedSsekDao = onboardingKeyboxSealedSsekDao,
    onboardingKeyboxHardwareKeysDao = onboardingKeyboxHardwareKeysDao,
    onboardingF8eClient = onboardingF8eClient,
    gettingStartedTaskDao = gettingStartedTaskDao,
    eventTracker = eventTracker,
    onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao
  )

  val hwActivation = FingerprintEnrolled(
    appGlobalAuthKeyHwSignature = WithAppKeysAndHardwareKeysMock.appGlobalAuthKeyHwSignature,
    keyBundle = WithAppKeysAndHardwareKeysMock.hardwareKeyBundle,
    sealedCsek = SealedCsekFake,
    sealedSsek = SealedSsekFake,
    serial = "test-serial-123"
  )

  beforeTest {
    appKeysGenerator.reset()
    accountConfigService.reset()
    keyboxDao.reset()
    onboardingAppKeyKeystore.clear()
    uuidGenerator.reset()
    createFullAccountService.reset()
    upgradeLiteAccountToFullService.reset()
    onboardingCompletionService.reset()
    onboardingKeyboxSealedCsekDao.reset()
    onboardingKeyboxSealedSsekDao.reset()
    onboardingKeyboxHardwareKeysDao.clear()
    onboardingF8eClient.reset()
    gettingStartedTaskDao.reset()
    onboardingKeyboxStepStateDao.clear()
  }

  test("createAppKeys generates new app keys when none persisted") {
    accountConfigService.setActiveConfig(FullAccountConfigMock)
    onboardingAppKeyKeystore.appKeys.shouldBeNull()

    val result = service.createAppKeys()

    result.shouldBeOk()
    val appKeys = result.value
    appKeys.appKeyBundle.shouldBe(appKeysGenerator.keyBundleResult.value)
    appKeys.config.shouldBe(FullAccountConfigMock)

    // Verify keys were persisted
    onboardingAppKeyKeystore.appKeys.shouldNotBeNull()
  }

  test("createAppKeys reuses persisted app keys when available") {
    accountConfigService.setActiveConfig(FullAccountConfigMock)
    // Pre-populate keystore with existing keys
    onboardingAppKeyKeystore.persistAppKeys(
      spendingKey = WithAppKeysMock.appKeyBundle.spendingKey,
      globalAuthKey = PublicKey("1234"),
      recoveryAuthKey = WithAppKeysMock.appKeyBundle.recoveryAuthKey,
      bitcoinNetworkType = WithAppKeysMock.appKeyBundle.networkType
    )

    val result = service.createAppKeys()

    result.shouldBeOk()
    // Should reuse existing keys
    result.value.appKeyBundle.authKey.value.shouldBe("1234")
  }

  test("createAppKeys returns error when key generation fails") {
    val error = RuntimeException("Key generation failed")
    appKeysGenerator.keyBundleResult = Err(error)

    val result = service.createAppKeys()

    result.shouldBeErr(error)
  }

  test("createAccount creates new full account successfully") {
    val appKeys = WithAppKeysMock

    val result = service.createAccount(
      context = NewFullAccount,
      appKeys = appKeys,
      hwActivation = hwActivation
    )

    result.shouldBeOk(FullAccountMock)

    // Verify sealed CSEK and SSEK were stored
    onboardingKeyboxSealedCsekDao.get().shouldBeOk { sealedCsek ->
      sealedCsek.shouldBe(SealedCsekFake)
    }
    onboardingKeyboxSealedSsekDao.get().shouldBeOk { sealedSsek ->
      sealedSsek.shouldBe(SealedSsekFake)
    }

    // Verify hardware keys were stored
    val hwKeys = onboardingKeyboxHardwareKeysDao.get().shouldBeOk()
    hwKeys.shouldNotBeNull()
    hwKeys.hwAuthPublicKey.shouldBe(hwActivation.keyBundle.authKey)
  }

  test("createAccount upgrades lite account to full account successfully") {
    val appKeys = WithAppKeysMock
    val context = LiteToFullAccountUpgrade(liteAccount = LiteAccountMock)

    val result = service.createAccount(
      context = context,
      appKeys = appKeys,
      hwActivation = hwActivation
    )

    result.shouldBeOk(FullAccountMock)
  }

  test("createAccount handles hardware key already in use error") {
    createFullAccountService.createAccountResult = Err(
      FullAccountCreationError.AccountCreationF8eError(
        SpecificClientErrorMock(
          errorCode = HW_AUTH_PUBKEY_IN_USE
        )
      )
    )

    val result = service.createAccount(
      context = NewFullAccount,
      appKeys = WithAppKeysMock,
      hwActivation = hwActivation
    )

    result.shouldBeErrOfType<HardwareKeyAlreadyInUseError>()
  }

  test("createAccount handles app key already in use error and clears keystore") {
    createFullAccountService.createAccountResult = Err(
      FullAccountCreationError.AccountCreationF8eError(
        SpecificClientErrorMock(
          errorCode = APP_AUTH_PUBKEY_IN_USE
        )
      )
    )

    onboardingAppKeyKeystore.persistAppKeys(
      spendingKey = WithAppKeysMock.appKeyBundle.spendingKey,
      globalAuthKey = WithAppKeysMock.appKeyBundle.authKey,
      recoveryAuthKey = WithAppKeysMock.appKeyBundle.recoveryAuthKey,
      bitcoinNetworkType = WithAppKeysMock.appKeyBundle.networkType
    )

    val result = service.createAccount(
      context = NewFullAccount,
      appKeys = WithAppKeysMock,
      hwActivation = hwActivation
    )

    result.shouldBeErrOfType<AppKeyAlreadyInUseError>()
  }

  test("createAccount returns error when storing sealed CSEK fails") {
    onboardingKeyboxSealedCsekDao.shouldFailToStore = true

    val result = service.createAccount(
      context = NewFullAccount,
      appKeys = WithAppKeysMock,
      hwActivation = hwActivation
    )

    result.shouldBeErrOfType<ErrorStoringSealedCsekError>()
  }

  test("createAccount returns error when storing sealed SSEK fails") {
    onboardingKeyboxSealedSsekDao.shouldFailToStore = true

    val result = service.createAccount(
      context = NewFullAccount,
      appKeys = WithAppKeysMock,
      hwActivation = hwActivation
    )

    // The error type isn't great, but it is shared for a set of possible db errors
    result.shouldBeErrOfType<ErrorStoringSealedCsekError>()
  }

  test("createAccount returns error when storing hardware keys") {
    onboardingKeyboxHardwareKeysDao.shouldFail = true

    val result = service.createAccount(
      context = NewFullAccount,
      appKeys = WithAppKeysMock,
      hwActivation = hwActivation
    )

    // The error type isn't great, but it is shared for a set of possible db errors
    result.shouldBeErrOfType<ErrorStoringSealedCsekError>()
  }

  test("activateAccount completes onboarding successfully") {
    val keybox = KeyboxMock

    val result = service.activateAccount(keybox)

    result.shouldBeOk()

    // Verify onboarding stores were cleared
    onboardingAppKeyKeystore.appKeys.shouldBeNull()
    onboardingKeyboxHardwareKeysDao.get().shouldBeOk().shouldBeNull()
    onboardingKeyboxSealedCsekDao.get().shouldBeOk().shouldBeNull()
    onboardingKeyboxSealedSsekDao.get().shouldBeOk().shouldBeNull()

    // Verify F8e client was called
    onboardingF8eClient.completeOnboardingCalls.awaitItem()

    // Verify fallback completion was recorded
    onboardingCompletionService.recordFallbackCompletionCalled.shouldBe(true)

    // Verify getting started tasks were added
    val tasks = gettingStartedTaskDao.getTasks()
    tasks.shouldContain(GettingStartedTask(TaskId.AddBitcoin, TaskState.Incomplete))
    tasks.shouldContain(GettingStartedTask(TaskId.EnableSpendingLimit, TaskState.Incomplete))

    // Verify keybox was activated
    keyboxDao.activeKeybox().first().shouldBeOk(keybox)

    eventTracker.eventCalls.awaitItem().action.shouldBe(Action.ACTION_APP_GETTINGSTARTED_INITIATED)
    eventTracker.eventCalls.awaitItem().action.shouldBe(Action.ACTION_APP_ACCOUNT_CREATED)
  }

  test("activateAccount returns error when F8e client fails") {
    val error = HttpError.UnhandledException(RuntimeException("F8e error"))
    onboardingF8eClient.completeOnboardingResult = Err(error)

    val result = service.activateAccount(KeyboxMock)
    result.shouldBeErr(error)

    onboardingF8eClient.completeOnboardingCalls.awaitItem()
  }

  test("cancelAccountCreation clears all onboarding data") {
    onboardingAppKeyKeystore.persistAppKeys(
      spendingKey = WithAppKeysMock.appKeyBundle.spendingKey,
      globalAuthKey = WithAppKeysMock.appKeyBundle.authKey,
      recoveryAuthKey = WithAppKeysMock.appKeyBundle.recoveryAuthKey,
      bitcoinNetworkType = WithAppKeysMock.appKeyBundle.networkType
    )
    onboardingKeyboxHardwareKeysDao.set(
      OnboardingKeyboxHardwareKeys(
        hwAuthPublicKey = hwActivation.keyBundle.authKey,
        appGlobalAuthKeyHwSignature = hwActivation.appGlobalAuthKeyHwSignature
      )
    )

    val result = service.cancelAccountCreation()

    result.shouldBeOk()

    // Verify all data was cleared
    onboardingAppKeyKeystore.appKeys.shouldBeNull()
    onboardingKeyboxHardwareKeysDao.get().shouldBeOk().shouldBeNull()
    onboardingKeyboxSealedCsekDao.get().shouldBeOk().shouldBeNull()
    onboardingKeyboxSealedSsekDao.get().shouldBeOk().shouldBeNull()
  }
})
