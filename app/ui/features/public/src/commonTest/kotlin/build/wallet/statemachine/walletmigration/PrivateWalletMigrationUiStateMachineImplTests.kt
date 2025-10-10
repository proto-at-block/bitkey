package build.wallet.statemachine.walletmigration

import build.wallet.auth.AccountAuthTokensMock
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.RefreshAuthTokensProps
import build.wallet.statemachine.auth.RefreshAuthTokensUiStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PrivateWalletMigrationUiStateMachineImplTests : FunSpec({
  val refreshAuthTokensUiStateMachine =
    object : RefreshAuthTokensUiStateMachine,
      ScreenStateMachineMock<RefreshAuthTokensProps>("refresh-auth-tokens") {}

  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc-session") {}

  val uuidGenerator = UuidGeneratorFake()

  val stateMachine = PrivateWalletMigrationUiStateMachineImpl(
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    refreshAuthTokensUiStateMachine = refreshAuthTokensUiStateMachine,
    uuidGenerator = uuidGenerator
  )

  val onMigrationCompleteCalls = turbines.create<FullAccount>("onMigrationComplete calls")
  val onExitCalls = turbines.create<Unit>("onExit calls")

  val props = PrivateWalletMigrationUiProps(
    account = FullAccountMock,
    onMigrationComplete = { onMigrationCompleteCalls.add(it) },
    onExit = { onExitCalls.add(Unit) }
  )

  test("successful migration flow") {
    stateMachine.test(props) {
      val mockProofOfPossession = HwFactorProofOfPossession("proof")
      val mockHwKeys = HwKeyBundle(
        localId = "test-uuid",
        spendingKey = HwSpendingPublicKey(DescriptorPublicKeyMock(identifier = "hardware-dpub-0")),
        authKey = HwAuthPublicKey(HwAuthSecp256k1PublicKeyMock.pubKey),
        networkType = FullAccountMock.keybox.config.bitcoinNetworkType
      )
      val sessionResult = KeysetInitiationNfcResult(
        proofOfPossession = mockProofOfPossession,
        newHwKeys = mockHwKeys
      )

      awaitBody<PrivateWalletMigrationIntroBodyModel> {
        onContinue()
      }

      awaitBodyMock<RefreshAuthTokensProps> {
        fullAccountId.shouldBe(FullAccountMock.accountId)
        appAuthKey.shouldBe(FullAccountMock.keybox.activeAppKeyBundle.authKey)
        onSuccess(AccountAuthTokensMock)
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<KeysetInitiationNfcResult>>(
        id = "nfc-session"
      ) {
        onSuccess(sessionResult)
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }
  }

  test("Back exits flow") {
    stateMachine.test(props) {
      awaitBody<PrivateWalletMigrationIntroBodyModel> {
        onBack()
      }

      onExitCalls.awaitItem()
    }
  }

  test("back during auth token refresh returns to intro") {
    stateMachine.test(props) {
      awaitBody<PrivateWalletMigrationIntroBodyModel> {
        onContinue()
      }

      awaitBodyMock<RefreshAuthTokensProps> {
        onBack()
      }

      awaitBody<PrivateWalletMigrationIntroBodyModel>()
    }
  }

  test("cancel during hardware initiation returns to intro") {
    stateMachine.test(props) {
      awaitBody<PrivateWalletMigrationIntroBodyModel> {
        onContinue()
      }

      awaitBodyMock<RefreshAuthTokensProps> {
        onSuccess(AccountAuthTokensMock)
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<KeysetInitiationNfcResult>>(
        id = "nfc-session"
      ) {
        onCancel()
      }

      awaitBody<PrivateWalletMigrationIntroBodyModel>()
    }
  }
})
