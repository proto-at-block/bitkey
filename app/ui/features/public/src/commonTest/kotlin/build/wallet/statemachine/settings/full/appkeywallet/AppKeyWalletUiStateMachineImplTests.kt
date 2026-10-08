package build.wallet.statemachine.settings.full.appkeywallet

import build.wallet.bitcoin.wallet.AppKeyWalletBalance
import build.wallet.bitcoin.wallet.AppKeyWalletServiceError
import build.wallet.bitcoin.wallet.AppKeyWalletServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryFake
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.money.amount.MoneyAmountModel
import build.wallet.statemachine.money.amount.MoneyAmountUiProps
import build.wallet.statemachine.money.amount.MoneyAmountUiStateMachine
import build.wallet.statemachine.settings.SettingsAppSegment
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBody
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AppKeyWalletUiStateMachineImplTests : FunSpec({
  val service = AppKeyWalletServiceFake(turbines::create)
  val moneyAmountUiStateMachine = object : MoneyAmountUiStateMachine,
    StateMachineMock<MoneyAmountUiProps, MoneyAmountModel>(
      MoneyAmountModel(primaryAmount = "1,000 sats", secondaryAmount = "$1.00")
    ) {}
  val stateMachine = AppKeyWalletUiStateMachineImpl(
    appKeyWalletService = service,
    moneyAmountUiStateMachine = moneyAmountUiStateMachine,
    fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryFake()
  )
  val backCalls = turbines.create<Unit>("back calls")
  val props = AppKeyWalletUiProps(FullAccountMock) { backCalls.add(Unit) }

  beforeTest {
    service.reset()
  }

  test("scan starts only after confirmation and displays its result") {
    service.fullScanResult = Ok(AppKeyWalletBalance(totalSats = 1000uL, spendableSats = 900uL))

    stateMachine.test(props) {
      awaitBody<FormBodyModel> { primaryButton?.onClick?.invoke() }
      awaitBody<LoadingSuccessBodyModel>()
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("Funds found")
        header?.sublineModel?.string.shouldBe("Found 900 spendable sats in your App Key Wallet.")
      }
      service.fullScanCalls.awaitItem().shouldBe(FullAccountMock.keybox.activeSpendingKeyset)
    }
  }

  test("scan failure includes ErrorData") {
    val error = AppKeyWalletServiceError.FullScanFailed(Error("electrum failed"))
    service.fullScanResult = Err(error)

    stateMachine.test(props) {
      awaitBody<FormBodyModel> { primaryButton?.onClick?.invoke() }
      awaitBody<LoadingSuccessBodyModel>()
      awaitBody<FormBodyModel> {
        errorData?.segment.shouldBe(SettingsAppSegment.AppKeyWallet)
        errorData?.actionDescription.shouldBe("Scanning App Key Wallet funds")
        errorData?.cause.shouldBe(error)
      }
      service.fullScanCalls.awaitItem()
    }
  }

  test("unavailable private key exits instead of retrying") {
    val error = AppKeyWalletServiceError.PrivateKeyUnavailable()
    service.fullScanResult = Err(error)

    stateMachine.test(props) {
      awaitBody<FormBodyModel> { primaryButton?.onClick?.invoke() }
      awaitBody<LoadingSuccessBodyModel>()
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("App key unavailable")
        primaryButton?.text.shouldBe("Done")
        errorData?.cause.shouldBe(error)
        primaryButton?.onClick?.invoke()
      }
      service.fullScanCalls.awaitItem()
      backCalls.awaitItem()
    }
  }

  test("scan can be canceled with back") {
    service.suspendScan = true

    stateMachine.test(props) {
      awaitBody<FormBodyModel> { primaryButton?.onClick?.invoke() }
      awaitBody<LoadingSuccessBodyModel> { onBack?.invoke() }
      service.fullScanCalls.awaitItem().shouldBe(FullAccountMock.keybox.activeSpendingKeyset)
      backCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }
})
