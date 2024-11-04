package build.wallet.statemachine.send

import app.cash.turbine.test
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.oneSatPerVbyteFeeRate
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.*
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.transactions.TransactionPriorityPreferenceFake
import build.wallet.bitcoin.transactions.TransactionsServiceFake
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.coroutines.turbine.turbines
import build.wallet.limit.MobilePayServiceMock
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.send.fee.FeeOptionListUiStateMachineFake
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.ui.model.icon.IconImage
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.collections.immutable.immutableMapOf

class TransferConfirmationUiStateMachineImplSellTests : FunSpec({

  // Initialize turbine instances with unique names incorporating the testNameSuffix
  val onTransferInitiatedCalls = turbines.create<Psbt>("transferInitiatedCalls-sell")
  val onTransferFailedCalls = turbines.create<Unit>("transferFailedCalls-sell")
  val onBackCalls = turbines.create<Unit>("backCalls-sell")
  val onExitCalls = turbines.create<Unit>("exitCalls-sell")

  // Mock PSBTs with unique IDs
  val appSignedPsbt = PsbtMock.copy(id = "app-signed-psbt-sell")
  val appAndHwSignedPsbt = PsbtMock.copy(id = "app-and-hw-signed-psbt-sell")

  // Define the initial TransactionDetailsModel
  val initialModel = TransactionDetailsModel(
    transactionSpeedText = "~10 minutes",
    transactionDetailModelType = TransactionDetailModelType.Sell(
      transferAmountText = "~$300.00",
      totalAmountPrimaryText = "$302.00",
      totalAmountSecondaryText = "546,347 sats",
      feeAmountText = "$2.00",
      feeAmountSecondaryText = "4,791 sats",
      transferAmountSecondaryText = "541,556 sats"
    ),
    amountLabel = "Amount selling"
  )

  // Initialize the TransactionDetailsCardUiStateMachine
  val transactionDetailsCardUiStateMachine = object : TransactionDetailsCardUiStateMachine,
    StateMachineMock<TransactionDetailsCardUiProps, TransactionDetailsModel>(
      initialModel = initialModel
    ) {}

  // Initialize the NfcSessionUIStateMachine
  val nfcSessionUIStateMachine = object : NfcSessionUIStateMachine,
    ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc-sell") {}

  // Define the TransferConfirmationUiProps with callbacks connected to the turbine instances
  @Suppress("DEPRECATION")
  val sellProps = TransferConfirmationUiProps(
    variant = TransferConfirmationScreenVariant.Sell("ExchangeX"),
    selectedPriority = FASTEST,
    account = FullAccountMock,
    recipientAddress = someBitcoinAddress,
    sendAmount = ExactAmount(BitcoinMoney.sats(123_456)),
    fees = immutableMapOf(
      FASTEST to Fee(BitcoinMoney.btc(10.0), oneSatPerVbyteFeeRate),
      THIRTY_MINUTES to Fee(BitcoinMoney.btc(2.0), oneSatPerVbyteFeeRate),
      SIXTY_MINUTES to Fee(BitcoinMoney.btc(1.0), oneSatPerVbyteFeeRate)
    ),
    exchangeRates = emptyImmutableList(),
    onTransferInitiated = { psbt, _ -> onTransferInitiatedCalls.add(psbt) },
    onTransferFailed = { onTransferFailedCalls.add(Unit) },
    onBack = { onBackCalls.add(Unit) },
    onExit = { onExitCalls.add(Unit) }
  )

  // Initialize shared services and dependencies
  val transactionPriorityPreference = TransactionPriorityPreferenceFake()
  val spendingWallet = SpendingWalletMock(turbines::create)
  val transactionsService = TransactionsServiceFake()
  val mobilePayService = MobilePayServiceMock(turbines::create)
  val feeOptionListUiStateMachine = FeeOptionListUiStateMachineFake()
  val appFunctionalityService = AppFunctionalityServiceFake()

  // Initialize the TransferConfirmationUiStateMachineImpl with all dependencies
  val stateMachine = TransferConfirmationUiStateMachineImpl(
    transactionDetailsCardUiStateMachine = transactionDetailsCardUiStateMachine,
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    transactionPriorityPreference = transactionPriorityPreference,
    feeOptionListUiStateMachine = feeOptionListUiStateMachine,
    transactionsService = transactionsService,
    mobilePayService = mobilePayService,
    appFunctionalityService = appFunctionalityService
  )

  // Reset mocks before each test
  beforeTest {
    spendingWallet.reset()
    transactionPriorityPreference.reset()
    transactionsService.reset()
    transactionsService.spendingWallet.value = spendingWallet
    mobilePayService.reset()
  }

  // Invoke the shared test function, passing in all necessary parameters
  transferConfirmationUiStateMachineTests(
    props = sellProps,
    onTransferInitiatedCalls = onTransferInitiatedCalls,
    onBackCalls = onBackCalls,
    onExitCalls = onExitCalls,
    stateMachine = stateMachine,
    spendingWallet = spendingWallet,
    transactionsService = transactionsService,
    transactionPriorityPreference = transactionPriorityPreference,
    mobilePayService = mobilePayService,
    appSignedPsbt = appSignedPsbt,
    appAndHwSignedPsbt = appAndHwSignedPsbt,
    nfcSessionUIStateMachineId = nfcSessionUIStateMachine.id
  )

  test("[app & hw] successful signing syncs, broadcasts, calls onTransferInitiated") {
    val transactionPriority = FASTEST
    spendingWallet.createSignedPsbtResult = Ok(appSignedPsbt)

    stateMachine.test(
      sellProps.copy(
        selectedPriority = transactionPriority
      )
    ) {
      // CreatingAppSignedPsbt
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(sellProps.sendAmount)

      // ViewingTransferConfirmation
      awaitScreenWithBody<FormBodyModel> {
        header.shouldNotBeNull().iconModel.shouldNotBeNull().iconImage.shouldBe(
          IconImage.LocalImage(
            Icon.Bitcoin
          )
        )
        header.shouldNotBeNull().headline.shouldBe("Confirm sale to ExchangeX")

        // Correct title, no fee selection enabled
        mainContentList[0]
          .shouldNotBeNull()
          .shouldBeTypeOf<DataList>()
          .items[0].apply {
          title.shouldBe("Est. arrival time")
          sideText.shouldBe("~10 minutes")
          secondarySideText.shouldBeNull()
          onClick.shouldBeNull()
          onTitle.shouldBeNull()
        }

        // Only show transfer amount and fee.
        mainContentList[1]
          .shouldNotBeNull()
          .shouldBeTypeOf<DataList>()
          .items[0].apply {
          title.shouldBe("Send to")
          sideText.shouldBe("ExchangeX")
        }

        mainContentList[2]
          .shouldNotBeNull()
          .shouldBeTypeOf<DataList>()
          .apply {
            items[0].apply {
              title.shouldBe("Amount selling")
              onClick.shouldBeNull()
              onTitle.shouldBeNull()
              sideText.shouldBe("~$300.00")
              secondarySideText.shouldBe("541,556 sats")
            }
            items[1].apply {
              title.shouldBe("Network Fees")
              onClick.shouldBeNull()
              onTitle.shouldNotBeNull()
              titleIcon.shouldNotBeNull()
                .iconImage
                .shouldBe(IconImage.LocalImage(Icon.SmallIconInformationFilled))
              sideText.shouldBe("$2.00")
              secondarySideText.shouldBe("4,791 sats")
            }
          }

        clickPrimaryButton()
      }

      // SigningWithHardware
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<Psbt>>(
        id = nfcSessionUIStateMachine.id
      ) {
        shouldShowLongRunningOperation.shouldBeTrue()
        onSuccess(appAndHwSignedPsbt)
      }

      // FinalizingAndBroadcastingTransaction
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      transactionsService.broadcastedPsbts.test {
        awaitItem().shouldContainOnly(appAndHwSignedPsbt)
      }
    }

    transactionPriorityPreference.preference.shouldBe(transactionPriority)
    onTransferInitiatedCalls.awaitItem()
  }
})
