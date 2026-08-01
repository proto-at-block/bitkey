package build.wallet.statemachine.send

import build.wallet.bdk.bindings.BdkUtxoMock
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.SendAll
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.transactions.PsbtsForSendAmount
import build.wallet.bitcoin.utxo.CoinControl
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.flags.PreBuiltPsbtFlowFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.USD
import build.wallet.money.exchange.ExchangeRateServiceFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.BodyStateMachineMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.send.utxo.UtxoSelectionUiProps
import build.wallet.statemachine.send.utxo.UtxoSelectionUiStateMachine
import build.wallet.statemachine.transactions.fee.FeeEstimationErrorUiProps
import build.wallet.statemachine.transactions.fee.FeeEstimationErrorUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.ionspin.kotlin.bignum.integer.toBigInteger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.collections.immutable.toImmutableList

class SendAmountEntryUiStateMachineImplTests : FunSpec({

  val featureFlagDao = FeatureFlagDaoMock()
  val preBuiltPsbtFlowFeatureFlag = PreBuiltPsbtFlowFeatureFlag(featureFlagDao)
  val bitcoinWalletService = BitcoinWalletServiceFake()
  val exchangeRateService = ExchangeRateServiceFake()

  val transferAmountEntryUiStateMachine =
    object : TransferAmountEntryUiStateMachine,
      ScreenStateMachineMock<TransferAmountEntryUiProps>("transfer-amount-entry") {}

  val utxoSelectionUiStateMachine =
    object : UtxoSelectionUiStateMachine,
      ScreenStateMachineMock<UtxoSelectionUiProps>("utxo-selection") {}

  val feeEstimationErrorUiStateMachine =
    object : FeeEstimationErrorUiStateMachine,
      BodyStateMachineMock<FeeEstimationErrorUiProps>("fee-estimation-error") {}

  val stateMachine = SendAmountEntryUiStateMachineImpl(
    transferAmountEntryUiStateMachine = transferAmountEntryUiStateMachine,
    utxoSelectionUiStateMachine = utxoSelectionUiStateMachine,
    preBuiltPsbtFlowFeatureFlag = preBuiltPsbtFlowFeatureFlag,
    bitcoinWalletService = bitcoinWalletService,
    feeEstimationErrorUiStateMachine = feeEstimationErrorUiStateMachine,
    moneyDisplayFormatter = MoneyDisplayFormatterFake
  )

  val recipientAddress = someBitcoinAddress
  val exchangeRates = exchangeRateService.exchangeRates.value.toImmutableList()

  val onContinueClickCalls = turbines.create<BitcoinTransactionSendAmount>("onContinueClick")
  val onContinueWithPreBuiltPsbtsCalls = turbines.create<Pair<BitcoinTransactionSendAmount, PsbtsForSendAmount>>("onContinueWithPreBuiltPsbts")

  beforeTest {
    bitcoinWalletService.reset()
    preBuiltPsbtFlowFeatureFlag.setFlagValue(false)
  }

  test("delegates to transfer amount entry and uses standard flow") {
    val props = SendAmountEntryUiProps(
      recipientAddress = recipientAddress,
      onBack = {},
      initialAmount = FiatMoney.zero(USD),
      exchangeRates = exchangeRates,
      onContinueClick = { onContinueClickCalls.add(it) }
    )

    stateMachine.test(props) {
      awaitBodyMock<TransferAmountEntryUiProps> {
        recipientAddress.shouldBe(recipientAddress)
        initialAmount.shouldBe(FiatMoney.zero(USD))

        onContinueClick(
          ContinueTransferParams(
            sendAmount = ExactAmount(BitcoinMoney.sats(10000.toBigInteger()))
          )
        )
      }

      onContinueClickCalls.awaitItem()
        .shouldBe(ExactAmount(BitcoinMoney.sats(10000.toBigInteger())))
    }
  }

  test("successfully builds PSBTs and continues with pre-built flow") {
    preBuiltPsbtFlowFeatureFlag.setFlagValue(true)

    val sendAmount = ExactAmount(BitcoinMoney.sats(10000.toBigInteger()))
    val mockPsbts = PsbtsForSendAmount(
      fastest = PsbtMock.copy(id = "test-psbt-fastest", amountSats = 10000UL),
      thirtyMinutes = PsbtMock.copy(id = "test-psbt-thirty", amountSats = 10000UL),
      sixtyMinutes = PsbtMock.copy(id = "test-psbt-sixty", amountSats = 10000UL)
    )

    bitcoinWalletService.createPsbtsForSendAmountResult = Ok(mockPsbts)

    val props = SendAmountEntryUiProps(
      recipientAddress = recipientAddress,
      onBack = {},
      initialAmount = FiatMoney.zero(USD),
      exchangeRates = exchangeRates,
      onContinueClick = { error("Should not call onContinueClick when pre-built PSBTs are enabled") },
      onContinueWithPreBuiltPsbts = { amount, psbts ->
        onContinueWithPreBuiltPsbtsCalls.add(amount to psbts)
      }
    )

    stateMachine.test(props) {
      // Step 1: Viewing calculator
      awaitBodyMock<TransferAmountEntryUiProps> {
        onContinueClick(ContinueTransferParams(sendAmount = sendAmount))
      }

      // Step 2: Building transactions (loading state)
      awaitBody<LoadingSuccessBodyModel> {
        // Loading state while building PSBTs
      }

      onContinueWithPreBuiltPsbtsCalls.awaitItem().let { (amount, psbts) ->
        amount.shouldBe(sendAmount)
        psbts.shouldBe(mockPsbts)
      }
    }
  }

  test("shows error when PSBT creation fails") {
    preBuiltPsbtFlowFeatureFlag.setFlagValue(true)

    val sendAmount = SendAll
    val error = Error("Failed to create PSBT")

    bitcoinWalletService.createPsbtsForSendAmountResult = Err(error)

    val props = SendAmountEntryUiProps(
      recipientAddress = recipientAddress,
      onBack = {},
      initialAmount = FiatMoney.zero(USD),
      exchangeRates = exchangeRates,
      onContinueClick = { error("Should not be called") }
    )

    stateMachine.test(props) {
      // Step 1: Viewing calculator
      awaitBodyMock<TransferAmountEntryUiProps> {
        onContinueClick(ContinueTransferParams(sendAmount = sendAmount))
      }

      // Step 2: Building transactions (loading state)
      awaitBody<LoadingSuccessBodyModel> {
        // Loading state
      }

      // Step 3: Error state
      awaitBodyMock<FeeEstimationErrorUiProps> {
        errorData.cause.shouldBe(error)
        errorData.actionDescription.shouldBe("Building pre-built PSBT for send transaction")
      }
    }
  }

  test("back from error state returns to calculator") {
    preBuiltPsbtFlowFeatureFlag.setFlagValue(true)

    val sendAmount = SendAll
    val error = Error("Failed to create PSBT")

    bitcoinWalletService.createPsbtsForSendAmountResult = Err(error)

    val props = SendAmountEntryUiProps(
      recipientAddress = recipientAddress,
      onBack = {},
      initialAmount = FiatMoney.zero(USD),
      exchangeRates = exchangeRates,
      onContinueClick = { }
    )

    stateMachine.test(props) {
      // Step 1: Viewing calculator
      awaitBodyMock<TransferAmountEntryUiProps> {
        onContinueClick(ContinueTransferParams(sendAmount = sendAmount))
      }

      // Step 2: Building transactions (loading)
      awaitBody<LoadingSuccessBodyModel> { }

      // Step 3: Error state
      awaitBodyMock<FeeEstimationErrorUiProps> {
        onBack()
      }

      // Step 4: Back to calculator
      awaitBodyMock<TransferAmountEntryUiProps> {
        initialAmount.shouldBe(FiatMoney.zero(USD))
      }
    }
  }

  test("back from loading state returns to calculator") {
    preBuiltPsbtFlowFeatureFlag.setFlagValue(true)

    val sendAmount = ExactAmount(BitcoinMoney.sats(10000.toBigInteger()))

    // Set up a successful PSBT creation
    val mockPsbts = PsbtsForSendAmount(
      fastest = PsbtMock,
      thirtyMinutes = PsbtMock,
      sixtyMinutes = PsbtMock
    )
    bitcoinWalletService.createPsbtsForSendAmountResult = Ok(mockPsbts)

    val props = SendAmountEntryUiProps(
      recipientAddress = recipientAddress,
      onBack = {},
      initialAmount = FiatMoney.zero(USD),
      exchangeRates = exchangeRates,
      onContinueClick = { }
    )

    stateMachine.test(props) {
      // Step 1: Viewing calculator
      awaitBodyMock<TransferAmountEntryUiProps> {
        onContinueClick(ContinueTransferParams(sendAmount = sendAmount))
      }

      // Step 2: Building transactions (loading)
      awaitBody<LoadingSuccessBodyModel> {
        onBack.shouldNotBe(null)
        onBack?.invoke()
      }

      // Step 3: Back to calculator
      awaitBodyMock<TransferAmountEntryUiProps> {
        initialAmount.shouldBe(FiatMoney.zero(USD))
      }
    }
  }

  test("passes coinControl into createPsbtsForSendAmount") {
    preBuiltPsbtFlowFeatureFlag.setFlagValue(true)

    val sendAmount = ExactAmount(BitcoinMoney.sats(1.toBigInteger()))
    val coinControl = CoinControl.create(
      inventory = setOf(BdkUtxoMock),
      selected = setOf(BdkUtxoMock.outPoint)
    ).get().shouldNotBeNull()
    val mockPsbts = PsbtsForSendAmount(
      fastest = PsbtMock,
      thirtyMinutes = PsbtMock,
      sixtyMinutes = PsbtMock
    )
    bitcoinWalletService.createPsbtsForSendAmountResult = Ok(mockPsbts)

    val props = SendAmountEntryUiProps(
      recipientAddress = recipientAddress,
      onBack = {},
      initialAmount = FiatMoney.zero(USD),
      exchangeRates = exchangeRates,
      coinControl = coinControl,
      onContinueClick = { error("Should not call onContinueClick") },
      onContinueWithPreBuiltPsbts = { amount, psbts ->
        onContinueWithPreBuiltPsbtsCalls.add(amount to psbts)
      }
    )

    stateMachine.test(props) {
      awaitBodyMock<TransferAmountEntryUiProps> {
        onContinueClick(ContinueTransferParams(sendAmount = sendAmount))
      }

      awaitBody<LoadingSuccessBodyModel> {}

      onContinueWithPreBuiltPsbtsCalls.awaitItem()
      bitcoinWalletService.lastCreatePsbtsCoinControl.shouldBe(coinControl)
    }
  }

  test("opens utxo picker and clears selection") {
    val onCoinControlChangedCalls = turbines.create<CoinControl?>("onCoinControlChanged")

    val props = SendAmountEntryUiProps(
      recipientAddress = recipientAddress,
      onBack = {},
      initialAmount = FiatMoney.zero(USD),
      exchangeRates = exchangeRates,
      onCoinControlChanged = { onCoinControlChangedCalls.add(it) },
      onContinueClick = {}
    )

    stateMachine.test(props) {
      awaitBodyMock<TransferAmountEntryUiProps> {
        val sendFlow = flow as TransferAmountEntryUiProps.Flow.Send
        sendFlow.onChooseCoinsClick.shouldNotBeNull().invoke(BitcoinMoney.sats(1000))
      }

      awaitBodyMock<UtxoSelectionUiProps> {
        onClear()
      }

      onCoinControlChangedCalls.awaitItem().shouldBeNull()

      awaitBodyMock<TransferAmountEntryUiProps> {}
    }
  }
})
