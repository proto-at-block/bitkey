package build.wallet.statemachine.inheritance.claims.start

import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.bitkey.inheritance.BeneficiaryLockedClaimBothDescriptorsFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.EndorsedBeneficiaryFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.inheritance.InheritanceServiceMock
import build.wallet.inheritance.InheritanceTransactionDetails
import build.wallet.money.BitcoinMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.exchange.ExchangeRateServiceFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.core.test
import build.wallet.statemachine.inheritance.claims.complete.CompleteInheritanceClaimUiStateMachineImpl
import build.wallet.statemachine.inheritance.claims.complete.CompleteInheritanceClaimUiStateMachineProps
import build.wallet.statemachine.inheritance.claims.complete.EmptyBenefactorWalletScreenModel
import build.wallet.statemachine.inheritance.claims.complete.InheritanceTransferConfirmationScreenModel
import build.wallet.statemachine.inheritance.claims.complete.InheritanceTransferSuccessScreenModel
import build.wallet.statemachine.ui.awaitUntilBody
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual

class CompleteInheritanceClaimUiStateMachineTests : FunSpec({
  val inheritanceWallet = SpendingWalletFake()
  val transferPbst = Psbt(
    id = "psbt-id",
    base64 = "some-base-64",
    fee = BitcoinMoney.sats(10_000),
    baseSize = 20_000,
    numOfInputs = 1,
    amountSats = 20_000UL
  )
  val transactionDetails = InheritanceTransactionDetails(
    claim = BeneficiaryLockedClaimBothDescriptorsFake,
    inheritanceWallet = inheritanceWallet,
    recipientAddress = BitcoinAddress("fake"),
    psbt = transferPbst
  )
  val inheritanceService = InheritanceServiceMock(
    loadApprovedClaimResult = Ok(transactionDetails),
    syncCalls = turbines.create("Sync Calls")
  )
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val currencyConverter = CurrencyConverterFake(
    conversionRate = 10_000.0
  )
  val exchangeRateService = ExchangeRateServiceFake()
  val moneyFormatter = MoneyDisplayFormatterFake

  val stateMachine = CompleteInheritanceClaimUiStateMachineImpl(
    inheritanceService = inheritanceService,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
    currencyConverter = currencyConverter,
    exchangeRateService = exchangeRateService,
    moneyFormatter = moneyFormatter
  )
  val exitCalls = turbines.create<Unit>("Exit Claim State Machine")
  val props = CompleteInheritanceClaimUiStateMachineProps(
    relationshipId = EndorsedBeneficiaryFake.id,
    account = FullAccountMock,
    onExit = { exitCalls.add(Unit) }
  )

  test("Complete inheritance transfer") {
    stateMachine.test(props) {
      awaitUntilBody<InheritanceTransferConfirmationScreenModel> {
        recipientAddress.shouldBeEqual("fake")
        amount.shouldBeEqual("$3.00")
        fees.shouldBeEqual("$1.00")
        netReceivePrimary.shouldBeEqual("$2.00")
        netReceiveSecondary.shouldBeEqual("20,000 sats")
        onTransfer()
      }
      awaitUntilBody<InheritanceTransferSuccessScreenModel> {
        recipientAddress.shouldBeEqual("fake")
        amount.shouldBeEqual("$3.00")
        fees.shouldBeEqual("$1.00")
        netReceivePrimary.shouldBeEqual("$2.00")
        netReceiveSecondary.shouldBeEqual("20,000 sats")
        onDone()
        exitCalls.awaitItem()
      }
    }
  }

  test("$0 balance error") {
    inheritanceService.loadApprovedClaimResult = Err(
      BdkError.InsufficientFunds(RuntimeException("Test"), "Test")
    )
    stateMachine.test(props) {
      awaitUntilBody<EmptyBenefactorWalletScreenModel> {
        onClose()
      }
      exitCalls.awaitItem()
    }
  }
})
