package build.wallet.statemachine.data.account.create.keybox

import build.wallet.auth.AccountCreationError
import build.wallet.auth.FullAccountCreatorMock
import build.wallet.auth.LiteToFullAccountUpgraderMock
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.bitkey.keybox.AppKeyBundleMock2
import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.bitkey.keybox.KeyCrossDraft
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.error.F8eError.ConnectivityError
import build.wallet.f8e.error.SpecificClientErrorMock
import build.wallet.f8e.error.code.CreateAccountClientErrorCode
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.keybox.keys.OnboardingAppKeyKeystoreFake
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrolled
import build.wallet.onboarding.OnboardingKeyboxHardwareKeysDaoFake
import build.wallet.onboarding.OnboardingKeyboxSealedCsekDaoMock
import build.wallet.platform.random.UuidFake
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.account.CreateFullAccountData
import build.wallet.statemachine.data.account.CreateFullAccountData.CreateKeyboxData.CreatingAppKeysData
import build.wallet.statemachine.data.account.CreateFullAccountData.CreateKeyboxData.HasAppAndHardwareKeysData
import build.wallet.statemachine.data.account.CreateFullAccountData.CreateKeyboxData.HasAppKeysData
import build.wallet.statemachine.data.account.CreateFullAccountData.CreateKeyboxData.PairingWithServerData
import build.wallet.statemachine.data.account.create.CreateFullAccountContext
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

class CreateKeyboxDataStateMachineImplTests : FunSpec({

  val onboardingKeyboxSealedCsekDao = OnboardingKeyboxSealedCsekDaoMock()
  val onboardingKeyboxHwAuthPublicKeyDao = OnboardingKeyboxHardwareKeysDaoFake()
  val appKeysGenerator = AppKeysGeneratorMock()
  val fullAccountCreator = FullAccountCreatorMock(turbine = turbines::create)
  val liteToFullAccountUpgrader = LiteToFullAccountUpgraderMock(turbines::create)

  val onboardingAppKeyKeystore = OnboardingAppKeyKeystoreFake()
  val uuid = UuidFake()

  val dataStateMachine =
    CreateKeyboxDataStateMachineImpl(
      fullAccountCreator = fullAccountCreator,
      appKeysGenerator = appKeysGenerator,
      onboardingKeyboxSealedCsekDao = onboardingKeyboxSealedCsekDao,
      onboardingKeyboxHardwareKeysDao = onboardingKeyboxHwAuthPublicKeyDao,
      uuid = uuid,
      onboardingAppKeyKeystore = onboardingAppKeyKeystore,
      liteToFullAccountUpgrader = liteToFullAccountUpgrader
    )

  val rollbackCalls = turbines.create<Unit>("rollback calls")

  val props =
    CreateKeyboxDataProps(
      templateFullAccountConfig = FullAccountConfigMock,
      context = CreateFullAccountContext.NewFullAccount,
      rollback = { rollbackCalls.add(Unit) }
    )

  beforeTest {
    appKeysGenerator.reset()
    onboardingAppKeyKeystore.clear()
    onboardingKeyboxSealedCsekDao.clear()
    onboardingKeyboxHwAuthPublicKeyDao.clear()
    uuid.reset()
  }

  test("create new keybox successfully") {
    val sealedCsek = SealedCsekFake
    dataStateMachine.test(props) {
      awaitItem().shouldBeInstanceOf<CreatingAppKeysData>()

      awaitItem().let {
        it.shouldBeTypeOf<HasAppKeysData>()
        it.appKeys.appKeyBundle.shouldBe(AppKeyBundleMock)
        it.onPairHardwareComplete(
          FingerprintEnrolled(
            appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
            keyBundle = HwKeyBundleMock,
            sealedCsek = sealedCsek,
            serial = ""
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<HasAppAndHardwareKeysData>()
      }

      awaitItem().shouldBeTypeOf<PairingWithServerData>()

      onboardingKeyboxSealedCsekDao.sealedCsek.shouldBe(sealedCsek)
      onboardingKeyboxHwAuthPublicKeyDao.keys!!.hwAuthPublicKey.shouldBe(HwKeyBundleMock.authKey)
      fullAccountCreator.createAccountCalls.awaitItem()
    }
  }

  test("create new keybox fail and successfully retry") {
    val sealedCsek = SealedCsekFake
    fullAccountCreator.createAccountResult =
      Err(
        AccountCreationError.AccountCreationF8eError(
          ConnectivityError(NetworkError(Throwable()))
        )
      )

    dataStateMachine.test(props) {
      awaitItem().shouldBeInstanceOf<CreatingAppKeysData>()

      awaitItem().let {
        it.shouldBeTypeOf<HasAppKeysData>()
        it.appKeys.appKeyBundle.shouldBe(AppKeyBundleMock)
        it.onPairHardwareComplete(
          FingerprintEnrolled(
            appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
            keyBundle = HwKeyBundleMock,
            sealedCsek = sealedCsek,
            serial = ""
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<HasAppAndHardwareKeysData>()
      }

      awaitItem().shouldBeTypeOf<PairingWithServerData>()

      fullAccountCreator.createAccountCalls.awaitItem()
        .shouldBeTypeOf<KeyCrossDraft.WithAppKeysAndHardwareKeys>()
        .appKeyBundle.apply {
          authKey.shouldBe(AppKeyBundleMock.authKey)
          spendingKey.shouldBe(AppKeyBundleMock.spendingKey)
          networkType.shouldBe(AppKeyBundleMock.networkType)
        }

      awaitItem().let {
        it.shouldBeTypeOf<CreateFullAccountData.CreateKeyboxData.CreateKeyboxErrorData>()
        fullAccountCreator.createAccountResult = Ok(FullAccountMock)
        it.primaryButton.onClick()
      }

      awaitItem().shouldBeTypeOf<PairingWithServerData>()

      onboardingKeyboxSealedCsekDao.sealedCsek.shouldNotBeNull()
      onboardingKeyboxHwAuthPublicKeyDao.keys!!.hwAuthPublicKey.shouldNotBeNull()
      fullAccountCreator.createAccountCalls.awaitItem()
    }
  }

  test("create new keybox fail due to already paired HW and rollback") {
    val sealedCsek = SealedCsekFake
    fullAccountCreator.createAccountResult =
      Err(
        AccountCreationError.AccountCreationF8eError(
          SpecificClientErrorMock(CreateAccountClientErrorCode.HW_AUTH_PUBKEY_IN_USE)
        )
      )
    dataStateMachine.test(props) {
      awaitItem().shouldBeInstanceOf<CreatingAppKeysData>()

      awaitItem().let {
        it.shouldBeTypeOf<HasAppKeysData>()
        it.appKeys.appKeyBundle.shouldBe(AppKeyBundleMock)
        it.onPairHardwareComplete(
          FingerprintEnrolled(
            appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
            keyBundle = HwKeyBundleMock,
            sealedCsek = sealedCsek,
            serial = ""
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<HasAppAndHardwareKeysData>()
      }

      awaitItem().shouldBeTypeOf<PairingWithServerData>()

      fullAccountCreator.createAccountCalls.awaitItem()
        .shouldBeTypeOf<KeyCrossDraft.WithAppKeysAndHardwareKeys>()
        .appKeyBundle.shouldBe(AppKeyBundleMock)

      awaitItem().let {
        it.shouldBeTypeOf<CreateFullAccountData.CreateKeyboxData.CreateKeyboxErrorData>()
        it.onBack()
      }

      rollbackCalls.awaitItem()
    }
  }

  test("create new keybox fail due to already paired app and retry") {
    val sealedCsek = SealedCsekFake
    fullAccountCreator.createAccountResult =
      Err(
        AccountCreationError.AccountCreationF8eError(
          SpecificClientErrorMock(CreateAccountClientErrorCode.APP_AUTH_PUBKEY_IN_USE)
        )
      )
    dataStateMachine.test(props) {
      awaitItem().shouldBeInstanceOf<CreatingAppKeysData>()

      awaitItem().let {
        it.shouldBeTypeOf<HasAppKeysData>()
        it.appKeys.appKeyBundle.shouldBe(AppKeyBundleMock)
        it.onPairHardwareComplete(
          FingerprintEnrolled(
            appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
            keyBundle = HwKeyBundleMock,
            sealedCsek = sealedCsek,
            serial = ""
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<HasAppAndHardwareKeysData>()
      }

      awaitItem().shouldBeTypeOf<PairingWithServerData>()

      val keyBundle =
        fullAccountCreator.createAccountCalls.awaitItem()
          .shouldBeTypeOf<KeyCrossDraft.WithAppKeysAndHardwareKeys>()
          .appKeyBundle.shouldBe(AppKeyBundleMock)

      awaitItem().let {
        it.shouldBeTypeOf<CreateFullAccountData.CreateKeyboxData.CreateKeyboxErrorData>()
        fullAccountCreator.createAccountResult = Ok(FullAccountMock)
        it.primaryButton.onClick()
      }

      awaitItem().shouldBeTypeOf<PairingWithServerData>()

      onboardingKeyboxSealedCsekDao.sealedCsek.shouldNotBeNull()
      onboardingKeyboxHwAuthPublicKeyDao.keys!!.hwAuthPublicKey.shouldNotBeNull()

      appKeysGenerator.keyBundleResult = Ok(AppKeyBundleMock2)

      val keyBundle2 =
        fullAccountCreator.createAccountCalls.awaitItem()
          .shouldBeTypeOf<KeyCrossDraft.WithAppKeysAndHardwareKeys>()

      keyBundle.shouldNotBeEqual(keyBundle2)
    }
  }

  test("creating a keybox from a previous onboarding session uses existing app keys") {
    onboardingAppKeyKeystore.persistAppKeys(
      spendingKey = AppKeyBundleMock.spendingKey,
      globalAuthKey = AppKeyBundleMock.authKey,
      recoveryAuthKey = AppKeyBundleMock.recoveryAuthKey,
      bitcoinNetworkType = SIGNET
    )

    dataStateMachine.test(props) {
      awaitItem().shouldBeInstanceOf<CreatingAppKeysData>()

      awaitItem().let {
        it.shouldBeTypeOf<HasAppKeysData>()
        it.appKeys.appKeyBundle.shouldBe(AppKeyBundleMock.copy(localId = "uuid-0"))
      }
    }
  }

  test("failed to store csek while trying to create keybox") {
    val sealedCsek = SealedCsekFake
    onboardingKeyboxSealedCsekDao.shouldFailToStore = true
    dataStateMachine.test(props) {
      awaitItem().shouldBeInstanceOf<CreatingAppKeysData>()

      awaitItem().let {
        it.shouldBeTypeOf<HasAppKeysData>()
        it.appKeys.appKeyBundle.shouldBe(AppKeyBundleMock)
        it.onPairHardwareComplete(
          FingerprintEnrolled(
            appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
            keyBundle = HwKeyBundleMock,
            sealedCsek = sealedCsek,
            serial = ""
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<HasAppAndHardwareKeysData>()
      }

      awaitItem().shouldBeTypeOf<CreateFullAccountData.CreateKeyboxData.CreateKeyboxErrorData>()
        .let {
          it.primaryButton.onClick()
        }

      awaitItem().shouldBeInstanceOf<HasAppKeysData>()

      onboardingKeyboxSealedCsekDao.sealedCsek.shouldBe(null)
    }
  }
})
