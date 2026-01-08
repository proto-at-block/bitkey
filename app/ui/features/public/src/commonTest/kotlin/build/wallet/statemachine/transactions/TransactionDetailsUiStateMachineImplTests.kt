package build.wallet.statemachine.transactions

import build.wallet.activity.Transaction
import build.wallet.activity.onChainDetails
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.explorer.BitcoinExplorerMock
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.*
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.*
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.partnerships.FakePartnershipTransaction
import build.wallet.partnerships.PartnershipTransactionStatus.PENDING
import build.wallet.partnerships.PartnershipTransactionStatus.SUCCESS
import build.wallet.partnerships.PartnershipTransactionType.SALE
import build.wallet.platform.clipboard.ClipItem
import build.wallet.platform.clipboard.ClipboardMock
import build.wallet.platform.haptics.HapticsMock
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.SmallIconInformationFilled
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.*
import build.wallet.statemachine.core.test
import build.wallet.statemachine.send.fee.FeeSelectionEventTrackerScreenId.FEE_ESTIMATION_FEE_RATE_TOO_LOW_ERROR_SCREEN
import build.wallet.statemachine.transactions.fee.FeeEstimationErrorUiStateMachineImpl
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.clickSecondaryButton
import build.wallet.time.ClockFake
import build.wallet.time.DateTimeFormatterMock
import build.wallet.time.DurationFormatterFake
import build.wallet.time.TimeZoneProviderMock
import build.wallet.ui.model.icon.IconImage
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

class TransactionDetailsUiStateMachineImplTests : FunSpec({

  val timeZoneProvider = TimeZoneProviderMock()
  val confirmedTime = BitcoinTransactionSend.confirmationTime()!!
  val broadcastTime = BitcoinTransactionSend.broadcastTime!!
  val estimatedConfirmationTime = BitcoinTransactionSend.estimatedConfirmationTime!!
  val timeToFormattedTime =
    mapOf(
      confirmedTime.toLocalDateTime(timeZoneProvider.current)
        to "confirmed-time",
      broadcastTime.toLocalDateTime(timeZoneProvider.current)
        to "broadcast-time",
      estimatedConfirmationTime.toLocalDateTime(timeZoneProvider.current)
        to "estimated-confirmation-time"
    )

  val clock = ClockFake()
  val durationFormatter = DurationFormatterFake()
  val eventTracker = EventTrackerMock(turbines::create)
  val bitcoinTransactionBumpabilityChecker =
    BitcoinTransactionBumpabilityCheckerFake(isBumpable = false)
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val feeBumpConfirmationUiStateMachine = object : FeeBumpConfirmationUiStateMachine,
    ScreenStateMachineMock<FeeBumpConfirmationProps>("fee-bump-confirmation") {}
  val spendingWallet = SpendingWalletMock(turbines::create)
  val bitcoinWalletService = BitcoinWalletServiceFake().apply {
    this.spendingWallet.value = spendingWallet
  }
  val feeEstimationErrorUiStateMachine = FeeEstimationErrorUiStateMachineImpl()

  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)
  val transactionActivityService = TransactionsActivityServiceFake()

  val clipboard = ClipboardMock()
  val haptics = HapticsMock()
  val speedUpTransactionService = SpeedUpTransactionServiceFake()

  val stateMachine =
    TransactionDetailsUiStateMachineImpl(
      bitcoinExplorer = BitcoinExplorerMock(),
      timeZoneProvider = timeZoneProvider,
      dateTimeFormatter = DateTimeFormatterMock(timeToFormattedTime = timeToFormattedTime),
      currencyConverter =
        CurrencyConverterFake(
          conversionRate = 3.0,
          historicalConversionRate = mapOf(broadcastTime to 4.0)
        ),
      moneyDisplayFormatter = MoneyDisplayFormatterFake,
      clock = clock,
      durationFormatter = durationFormatter,
      eventTracker = eventTracker,
      bitcoinTransactionBumpabilityChecker = bitcoinTransactionBumpabilityChecker,
      fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
      feeBumpConfirmationUiStateMachine = feeBumpConfirmationUiStateMachine,
      speedUpTransactionService = speedUpTransactionService,
      inAppBrowserNavigator = inAppBrowserNavigator,
      bitcoinWalletService = bitcoinWalletService,
      transactionsActivityService = transactionActivityService,
      clipboard = clipboard,
      haptics = haptics,
      feeEstimationErrorUiStateMachine = feeEstimationErrorUiStateMachine
    )

  val receivedProps =
    TransactionDetailsUiProps(
      account = FullAccountMock,
      transaction = Transaction.BitcoinWalletTransaction(BitcoinTransactionReceive),
      onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
    )

  val sentProps =
    TransactionDetailsUiProps(
      account = FullAccountMock,
      transaction = Transaction.BitcoinWalletTransaction(BitcoinTransactionSend),
      onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
    )

  val utxoConsolidationProps =
    TransactionDetailsUiProps(
      account = FullAccountMock,
      transaction = Transaction.BitcoinWalletTransaction(BitcoinTransactionUtxoConsolidation),
      onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
    )

  val pendingReceiveProps =
    TransactionDetailsUiProps(
      account = FullAccountMock,
      transaction = Transaction.BitcoinWalletTransaction(
        BitcoinTransactionReceive.copy(
          confirmationStatus = Pending
        )
      ),
      onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
    )

  val pendingSentProps =
    TransactionDetailsUiProps(
      account = FullAccountMock,
      transaction = Transaction.BitcoinWalletTransaction(
        BitcoinTransactionSend.copy(
          confirmationStatus = Pending
        )
      ),
      onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
    )

  val pendingUtxoConsolidationProps =
    TransactionDetailsUiProps(
      account = FullAccountMock,
      transaction = Transaction.BitcoinWalletTransaction(
        BitcoinTransactionUtxoConsolidation.copy(
          confirmationStatus = Pending
        )
      ),
      onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
    )

  val pendingSentPropsNoEstimatedConfirmationTime =
    TransactionDetailsUiProps(
      account = FullAccountMock,
      transaction = Transaction.BitcoinWalletTransaction(
        BitcoinTransactionSend.copy(
          confirmationStatus = Pending,
          estimatedConfirmationTime = null
        )
      ),
      onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
    )

  val partnershipPurchaseProps = TransactionDetailsUiProps(
    account = FullAccountMock,
    transaction = Transaction.PartnershipTransaction(
      details = FakePartnershipTransaction.copy(
        status = PENDING
      ),
      bitcoinTransaction = BitcoinTransactionReceive.copy(
        confirmationStatus = Pending,
        estimatedConfirmationTime = null
      )
    ),
    onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
  )

  beforeTest {
    bitcoinWalletService.reset()
    bitcoinWalletService.spendingWallet.value = spendingWallet
    bitcoinTransactionBumpabilityChecker.isBumpable = false
    transactionActivityService.reset()
  }

  test("pending receive transaction returns correct model") {
    stateMachine.test(pendingReceiveProps) {
      awaitBody<TransactionDetailModel> {
        // before currency conversion

        testButtonsAndHeader(
          transaction = pendingReceiveProps.transaction,
          isPending = true,
          transactionType = Incoming,
          isLate = false
        )

        content[0].shouldBeInstanceOf<StepperIndicator>()
        content[1].shouldBeInstanceOf<Divider>()

        // Transaction ID
        content[2].shouldBeInstanceOf<DataList>()

        // Amount Details
        with(content[3].shouldBeInstanceOf<DataList>()) {
          items[0]
            .shouldNotBeNull()
            .expect(
              title = "Amount",
              sideText = "100,000,000 sats"
            )
        }
      }

      awaitBody<TransactionDetailModel> {
        // after currency conversion
        // Should use the current exchange rate

        with(content[3].shouldBeInstanceOf<DataList>()) {
          items[0]
            .shouldNotBeNull()
            .expect(
              title = "Amount",
              sideText = "$3.00",
              secondarySideText = "100,000,000 sats"
            )
        }
      }
    }
  }

  test("received transactions returns correct model") {
    stateMachine.test(receivedProps) {
      awaitBody<TransactionDetailModel> {
        // before currency conversion

        testButtonsAndHeader(
          transaction = receivedProps.transaction,
          isPending = false,
          transactionType = Incoming,
          isLate = false
        )

        content[0].shouldBeInstanceOf<StepperIndicator>()
        content[1].shouldBeInstanceOf<Divider>()

        // Time Details
        content[2]
          .shouldBeInstanceOf<DataList>()
          .items[0]
          .expect(title = "Confirmed", sideText = "confirmed-time")

        // Transaction ID
        content[3].shouldBeInstanceOf<DataList>()

        // Amount Details
        with(content[4].shouldBeInstanceOf<DataList>()) {
          items[0]
            .shouldNotBeNull()
            .expect(
              title = "Amount",
              sideText = "100,000,000 sats"
            )
        }
      }

      awaitBody<TransactionDetailModel> {
        // after currency conversion
        // Should use the current exchange rate
        with(content[4].shouldBeInstanceOf<DataList>()) {
          items[0]
            .shouldNotBeNull()
            .expect(
              title = "Amount",
              sideText = "$3.00",
              secondarySideText = "100,000,000 sats"
            )
        }
      }
    }
  }

  test("pending sent transaction returns correct model") {
    stateMachine.test(pendingSentProps) {
      awaitBody<TransactionDetailModel> {
        // before currency conversion

        testButtonsAndHeader(
          transaction = pendingSentProps.transaction,
          isPending = true,
          transactionType = Outgoing,
          isLate = false
        )

        content[0].shouldBeInstanceOf<StepperIndicator>()
        content[1].shouldBeInstanceOf<Divider>()

        // Time Details
        with(content[2].shouldBeInstanceOf<DataList>()) {
          items[0].expect(title = "Arrival time", sideText = "estimated-confirmation-time")
        }

        // Transaction ID
        content[3].shouldBeInstanceOf<DataList>()

        // Amount Details
        with(content[4].shouldBeInstanceOf<DataList>()) {
          items[0].expect(
            title = "Amount",
            sideText = "100,000,000 sats"
          )
          items[1].expect(
            title = "Network fees",
            sideText = "1,000,000 sats"
          )
          total
            .shouldNotBeNull()
            .expect(
              title = "Total",
              sideText = "$0.00",
              secondarySideText = "101,000,000 sats"
            )
        }
      }

      awaitBody<TransactionDetailModel> {
        // after currency conversion
        // Should use the historical exchange rate for broadcast time
        with(content[4].shouldBeInstanceOf<DataList>()) {
          items[0].expect(
            title = "Amount",
            sideText = "$4.00",
            secondarySideText = "100,000,000 sats"
          )
          items[1].expect(
            title = "Network fees",
            sideText = "$0.04",
            secondarySideText = "1,000,000 sats"
          )
          total
            .shouldNotBeNull()
            .expect(
              title = "Total",
              sideText = "$4.04",
              secondarySideText = "101,000,000 sats"
            )
        }
      }
    }
  }

  test("pending send transaction without estimate does not show confirmation row") {
    stateMachine.test(pendingSentPropsNoEstimatedConfirmationTime) {
      awaitBody<TransactionDetailModel> {
        // before currency conversion

        testButtonsAndHeader(
          transaction = pendingSentPropsNoEstimatedConfirmationTime.transaction,
          isPending = true,
          transactionType = Outgoing,
          isLate = false
        )

        content[0].shouldBeInstanceOf<StepperIndicator>()
        content[1].shouldBeInstanceOf<Divider>()

        // Transaction ID
        content[2].shouldBeInstanceOf<DataList>()

        // Amount Details (no time details shown for this case)
        with(content[3].shouldBeInstanceOf<DataList>()) {
          items[0].expect(title = "Amount", sideText = "100,000,000 sats")
        }
      }

      // after currency conversion
      awaitBody<TransactionDetailModel>()
    }
  }

  test("sent transactions returns correct model") {
    stateMachine.test(sentProps) {
      awaitBody<TransactionDetailModel> {
        // before currency conversion

        testButtonsAndHeader(
          transaction = sentProps.transaction,
          isPending = false,
          transactionType = Outgoing,
          isLate = false
        )

        content[0].shouldBeInstanceOf<StepperIndicator>()
        content[1].shouldBeInstanceOf<Divider>()

        // Time Details
        with(content[2].shouldBeInstanceOf<DataList>()) {
          items[0].expect(title = "Confirmed", sideText = "confirmed-time")
        }

        // Transaction ID
        content[3].shouldBeInstanceOf<DataList>()

        // Amount Details
        with(content[4].shouldBeInstanceOf<DataList>()) {
          items[0].expect(
            title = "Amount",
            sideText = "100,000,000 sats"
          )
          items[1].expect(
            title = "Network fees",
            sideText = "1,000,000 sats"
          )
          total
            .shouldNotBeNull()
            .expect(
              title = "Total",
              sideText = "$0.00",
              secondarySideText = "101,000,000 sats"
            )
        }
      }

      awaitBody<TransactionDetailModel> {
        // after currency conversion
        // Should use the historical exchange rate
        with(content[4].shouldBeInstanceOf<DataList>()) {
          items[0].expect(
            title = "Amount",
            sideText = "$4.00",
            secondarySideText = "100,000,000 sats"
          )
          items[1].expect(
            title = "Network fees",
            sideText = "$0.04",
            secondarySideText = "1,000,000 sats"
          )
          total
            .shouldNotBeNull()
            .expect(
              title = "Total",
              sideText = "$4.04",
              secondarySideText = "101,000,000 sats"
            )
        }
      }
    }
  }

  test("onClose is called") {
    stateMachine.test(pendingReceiveProps) {
      awaitBody<FormBodyModel> {
        onBack?.invoke()
      }
      inAppBrowserNavigator.onCloseCalls.awaitItem().shouldBe(Unit)

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("browser navigation opens on primary button click") {
    stateMachine.test(pendingReceiveProps) {
      awaitBody<TransactionDetailModel>()

      awaitBody<TransactionDetailModel> {
        viewTransactionText.shouldBe("View transaction")
        onViewTransaction()
      }

      inAppBrowserNavigator.onOpenCalls
        .awaitItem()
        .shouldBe(
          "https://bitkey.mempool.space/tx/4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b"
        )
    }
  }

  test("transaction updates trigger screen updates") {
    stateMachine.test(pendingSentProps) {
      awaitBody<TransactionDetailModel> {
        // Time Details
        with(content[2].shouldBeInstanceOf<DataList>()) {
          items[0].expect(title = "Arrival time", sideText = "estimated-confirmation-time")
        }
      }

      transactionActivityService.transactions.value = listOf(sentProps.transaction)

      awaitUntilBody<TransactionDetailModel>(
        matching = { it.content[2].shouldBeInstanceOf<DataList>().items[0].title == "Confirmed" }
      ) {
        // Time Details
        with(content[2].shouldBeInstanceOf<DataList>()) {
          items[0].expect(title = "Confirmed", sideText = "confirmed-time")
        }
      }
    }
  }

  context("Speed up feature flag is on") {
    beforeTest {
      bitcoinTransactionBumpabilityChecker.isBumpable = true
    }

    test("pending sent transaction returns correct model") {
      stateMachine.test(pendingSentProps) {
        awaitBody<TransactionDetailModel> {
          // before currency conversion

          testButtonsAndHeader(
            transaction = pendingSentProps.transaction,
            isSpeedUpOn = true,
            isPending = true,
            transactionType = Outgoing,
            isLate = false
          )

          // Time Details
          with(content[2].shouldBeInstanceOf<DataList>()) {
            items[0].expect(title = "Arrival time", sideText = "estimated-confirmation-time")
          }

          // Transaction ID
          content[3].shouldBeInstanceOf<DataList>()

          // Amount Details
          with(content[4].shouldBeInstanceOf<DataList>()) {
            items[0].expect(
              title = "Amount",
              sideText = "100,000,000 sats"
            )
            items[1].expect(
              title = "Network fees",
              sideText = "1,000,000 sats"
            )
            total
              .shouldNotBeNull()
              .expect(
                title = "Total",
                sideText = "$0.00",
                secondarySideText = "101,000,000 sats"
              )
          }
        }

        awaitBody<TransactionDetailModel> {
          // after currency conversion
          // Should use the historical exchange rate for broadcast time
          with(content[4].shouldBeInstanceOf<DataList>()) {
            items[0].expect(
              title = "Amount",
              sideText = "$4.00",
              secondarySideText = "100,000,000 sats"
            )
            items[1].expect(
              title = "Network fees",
              sideText = "$0.04",
              secondarySideText = "1,000,000 sats"
            )
            total
              .shouldNotBeNull()
              .expect(
                title = "Total",
                sideText = "$4.04",
                secondarySideText = "101,000,000 sats"
              )
          }
        }
      }
    }

    test("correctly show late notice if current time is after promised confirmation time") {
      // Set clock to return some time that is after transaction estimated confirmation time.
      clock.now =
        pendingSentProps.transaction.onChainDetails()!!.estimatedConfirmationTime!!.plus(10.minutes)

      stateMachine.test(pendingSentProps) {
        awaitBody<TransactionDetailModel> {
          testButtonsAndHeader(
            transaction = pendingSentProps.transaction,
            isSpeedUpOn = true,
            isPending = true,
            transactionType = Outgoing,
            isLate = true
          )

          with(content[2].shouldBeInstanceOf<DataList>()) {
            items[0].title.shouldBe("Should have arrived by")
            items[0].sideTextTreatment.shouldBe(DataList.Data.SideTextTreatment.STRIKETHROUGH)
            items[0].sideTextType.shouldBe(DataList.Data.SideTextType.REGULAR)
            items[0].secondarySideText.shouldNotBeNull()
            items[0].secondarySideTextType.shouldBe(DataList.Data.SideTextType.BOLD)
            items[0].secondarySideTextTreatment.shouldBe(DataList.Data.SideTextTreatment.WARNING)
            items[0].explainer.shouldNotBeNull()
            items[0].explainer?.iconButton.shouldNotBeNull()
            items[0]
              .explainer
              ?.iconButton
              ?.iconModel
              ?.iconImage
              .shouldBeTypeOf<IconImage.LocalImage>()
              .icon.shouldBe(SmallIconInformationFilled)
          }
        }

        // after currency conversion
        awaitBody<FormBodyModel>()
      }
    }

    test("tapping explainer info icon should open education sheet") {
      // Set clock to return some time that is after transaction estimated confirmation time.
      clock.now =
        pendingSentProps.transaction.onChainDetails()!!.estimatedConfirmationTime!!.plus(10.minutes)

      stateMachine.test(pendingSentProps) {
        awaitBody<TransactionDetailModel> {
          testButtonsAndHeader(
            transaction = pendingSentProps.transaction,
            isSpeedUpOn = true,
            isPending = true,
            transactionType = Outgoing,
            isLate = true
          )
        }

        // after currency conversion - click the explainer icon here
        awaitBody<TransactionDetailModel> {
          with(mainContentList[2].shouldBeInstanceOf<DataList>()) {
            items[0]
              .explainer
              ?.iconButton
              ?.iconModel
              ?.iconImage
              .shouldBeTypeOf<IconImage.LocalImage>()
              .icon.shouldBe(SmallIconInformationFilled)
            items[0]
              .explainer
              ?.iconButton
              ?.onClick
              ?.invoke()
          }
        }

        // after clicking explainer icon button
        val screenModel = awaitItem()
        val bottomSheet = screenModel.bottomSheetModel.shouldNotBeNull()
        with(bottomSheet.body.shouldBeInstanceOf<FormBodyModel>()) {
          with(header.shouldNotBeNull()) {
            headline.shouldBe("Speed up transactions")
          }
          primaryButton
            .shouldNotBeNull()
            .text
            .shouldBe("Try speeding up")

          this.onBack?.invoke()
        }

        // after closing education sheet
        awaitBody<FormBodyModel>()
      }
    }

    test("tapping speed up should open the fee bump flow") {
      speedUpTransactionService.result = Ok(
        SpeedUpTransactionResult(
          psbt = PsbtMock,
          newFeeRate = FeeRate(satsPerVByte = 10.0f),
          details = SpeedUpTransactionDetails(
            txid = TX_FAKE_ID,
            recipientAddress = someBitcoinAddress,
            sendAmount = BitcoinMoney.btc(1.0),
            oldFee = Fee(
              amount = BitcoinMoney.sats(1_000_000)
            ),
            transactionType = Outgoing
          )
        )
      )
      stateMachine.test(pendingSentProps) {
        awaitBody<TransactionDetailModel> {
          testButtonsAndHeader(
            transaction = pendingSentProps.transaction,
            isSpeedUpOn = true,
            isPending = true,
            transactionType = Outgoing,
            isLate = true
          )
        }

        // after currency conversion
        awaitBody<TransactionDetailModel> {
          onSpeedUpTransaction()
        }

        // Ensure we log analytics event
        eventTracker.eventCalls.awaitItem()

        // loading the fee rates and fetching wallet
        awaitBody<FormBodyModel>()

        // Show fee bump flow
        awaitBodyMock<FeeBumpConfirmationProps>()
      }
    }

    test("tapping speed up with insufficient balance to bump fee should show error screen") {
      speedUpTransactionService.result = Err(SpeedUpTransactionError.InsufficientFunds)
      stateMachine.test(pendingSentProps) {
        awaitBody<TransactionDetailModel> {
          testButtonsAndHeader(
            transaction = pendingSentProps.transaction,
            isSpeedUpOn = true,
            isPending = true,
            transactionType = Outgoing,
            isLate = true
          )
        }

        // after currency conversion
        awaitBody<TransactionDetailModel> {
          onSpeedUpTransaction()
        }

        // loading the fee rates and fetching wallet
        awaitBody<FormBodyModel>()

        // failed to launch fee bump flow from insufficient funds
        awaitBody<FormBodyModel> {
          header.shouldNotBeNull()
            .headline
            .shouldBe("We couldn’t speed up this transaction")
        }

        // Ensure we log analytics event
        eventTracker.eventCalls
          .awaitItem()
          .shouldBe(TrackedAction(Action.ACTION_APP_ATTEMPT_SPEED_UP_TRANSACTION))
      }
    }

    test("tapping speed up when fee rates are too low should show error screen") {
      speedUpTransactionService.result = Err(SpeedUpTransactionError.FeeRateTooLow)
      stateMachine.test(pendingSentProps) {
        awaitBody<TransactionDetailModel> {
          testButtonsAndHeader(
            transaction = pendingSentProps.transaction,
            isSpeedUpOn = true,
            isPending = true,
            transactionType = Outgoing,
            isLate = true
          )
        }

        // after currency conversion
        awaitBody<TransactionDetailModel> {
          clickSecondaryButton()
        }

        // loading the fee rates and fetching wallet
        awaitBody<FormBodyModel>()

        // failed to launch fee bump flow from insufficient funds
        awaitBody<FormBodyModel> {
          header.shouldNotBeNull()
            .apply {
              headline
                .shouldBe("We couldn’t speed up this transaction")
              sublineModel
                .shouldNotBeNull()
                .string
                .shouldBe("The current fee rate is too low. Please try again later.")
            }
          id.shouldBe(FEE_ESTIMATION_FEE_RATE_TOO_LOW_ERROR_SCREEN)
        }

        // Ensure we log analytics event
        eventTracker.eventCalls
          .awaitItem()
          .shouldBe(TrackedAction(Action.ACTION_APP_ATTEMPT_SPEED_UP_TRANSACTION))
      }
    }

    test("speed up button hides once transaction confirms") {
      stateMachine.test(pendingSentProps) {
        awaitBody<TransactionDetailModel> {
          feeBumpEnabled.shouldBeTrue()
          secondaryButton.shouldNotBeNull()
            .text.shouldBe("Speed Up")
        }

        awaitBody<TransactionDetailModel> {
          feeBumpEnabled.shouldBeTrue()
          secondaryButton.shouldNotBeNull()
        }

        transactionActivityService.transactions.value = listOf(sentProps.transaction)

        awaitUntilBody<TransactionDetailModel>(
          matching = {
            it.formHeaderModel == confirmedFormHeaderModel(sentProps.transaction) &&
              !it.feeBumpEnabled
          }
        ) {
          feeBumpEnabled.shouldBeFalse()
          secondaryButton.shouldBeNull()
        }
      }
    }
  }

  context("utxo consolidation") {
    test("pending utxo consolidation transaction returns correct model") {
      stateMachine.test(pendingUtxoConsolidationProps) {
        awaitBody<TransactionDetailModel> {
          // before currency conversion

          testButtonsAndHeader(
            transaction = pendingUtxoConsolidationProps.transaction,
            isPending = true,
            transactionType = UtxoConsolidation,
            isLate = false
          )

          content[0].shouldBeInstanceOf<StepperIndicator>()
          content[1].shouldBeInstanceOf<Divider>()

          // Transaction ID
          content[2].shouldBeInstanceOf<DataList>()

          // Amount Details
          with(content[3].shouldBeInstanceOf<DataList>()) {
            items[0].expect(title = "UTXOs consolidated", sideText = "2 → 1")
            items[1].expect(
              title = "Consolidation cost",
              sideText = "10,000,000 sats"
            )
            total.shouldBeNull()
          }
        }

        awaitBody<TransactionDetailModel> {
          // after currency conversion
          // Should use the current exchange rate
          with(content[3].shouldBeInstanceOf<DataList>()) {
            items[1].expect(
              title = "Consolidation cost",
              sideText = "$0.30",
              secondarySideText = "10,000,000 sats"
            )
            total.shouldBeNull()
          }
        }
      }
    }

    test("utxo consolidation transaction returns correct model") {
      stateMachine.test(utxoConsolidationProps) {
        awaitBody<TransactionDetailModel> {
          // before currency conversion

          testButtonsAndHeader(
            transaction = pendingUtxoConsolidationProps.transaction,
            isPending = false,
            transactionType = UtxoConsolidation,
            isLate = false
          )

          content[0].shouldBeInstanceOf<StepperIndicator>()
          content[1].shouldBeInstanceOf<Divider>()

          // Time Details
          content[2]
            .shouldBeInstanceOf<DataList>()
            .items[0]
            .expect(title = "Confirmed", sideText = "confirmed-time")

          // Transaction ID
          content[3].shouldBeInstanceOf<DataList>()

          // Amount Details
          with(content[4].shouldBeInstanceOf<DataList>()) {
            items[0].expect(title = "UTXOs consolidated", sideText = "2 → 1")
            items[1].expect(
              title = "Consolidation cost",
              sideText = "10,000,000 sats"
            )
            total.shouldBeNull()
          }
        }

        awaitBody<TransactionDetailModel> {
          // after currency conversion
          // Should use the current exchange rate
          with(content[4].shouldBeInstanceOf<DataList>()) {
            items[1].expect(
              title = "Consolidation cost",
              sideText = "$0.30",
              secondarySideText = "10,000,000 sats"
            )
            total.shouldBeNull()
          }
        }
      }
    }
  }

  context("partner transaction") {
    test("purchase") {
      stateMachine.test(partnershipPurchaseProps) {
        awaitBody<TransactionDetailModel> {
          // before currency conversion

          testButtonsAndHeader(
            transaction = partnershipPurchaseProps.transaction,
            isPending = true,
            transactionType = Incoming,
            isLate = false
          )

          content[0].shouldBeInstanceOf<StepperIndicator>()
          content[1].shouldBeInstanceOf<Divider>()

          // Transaction ID
          content[2].shouldBeInstanceOf<DataList>()

          // Amount Details
          with(content[3].shouldBeInstanceOf<DataList>()) {
            items[0]
              .shouldNotBeNull()
              .expect(
                title = "Amount",
                sideText = "100,000,000 sats"
              )
          }
        }

        awaitBody<TransactionDetailModel> {
          // after currency conversion
          // Should use the current exchange rate

          with(content[3].shouldBeInstanceOf<DataList>()) {
            items[0]
              .shouldNotBeNull()
              .expect(
                title = "Amount",
                sideText = "$3.00",
                secondarySideText = "100,000,000 sats"
              )
          }
        }
      }
    }

    test("sale") {
      val props = partnershipPurchaseProps.copy(
        transaction = Transaction.PartnershipTransaction(
          details = FakePartnershipTransaction.copy(
            status = SUCCESS,
            type = SALE
          ),
          bitcoinTransaction = BitcoinTransactionSend
        )
      )
      stateMachine.test(props) {
        awaitBody<TransactionDetailModel> {
          // before currency conversion

          testButtonsAndHeader(
            transaction = props.transaction,
            isPending = false,
            transactionType = Outgoing,
            isLate = false
          )

          content[0].shouldBe(completeTransactionStepper)
          content[1].shouldBe(Divider)

          // Time Details
          with(content[2].shouldBeInstanceOf<DataList>()) {
            items[0].expect(title = "Confirmed", sideText = "confirmed-time")
          }

          // Transaction ID
          content[3].shouldBeInstanceOf<DataList>()

          // Amount Details
          with(content[4].shouldBeInstanceOf<DataList>()) {
            items[0].expect(
              title = "Amount",
              sideText = "100,000,000 sats"
            )
            items[1].expect(
              title = "Network fees",
              sideText = "1,000,000 sats"
            )
            total
              .shouldNotBeNull()
              .expect(
                title = "Total",
                sideText = "$0.00",
                secondarySideText = "101,000,000 sats"
              )
          }
        }

        awaitBody<TransactionDetailModel> {
          // after currency conversion
          // Should use the historical exchange rate
          with(content[4].shouldBeInstanceOf<DataList>()) {
            items[0].expect(
              title = "Amount",
              sideText = "$4.00",
              secondarySideText = "100,000,000 sats"
            )
            items[1].expect(
              title = "Network fees",
              sideText = "$0.04",
              secondarySideText = "1,000,000 sats"
            )
            total
              .shouldNotBeNull()
              .expect(
                title = "Total",
                sideText = "$4.04",
                secondarySideText = "101,000,000 sats"
              )
          }
        }
      }
    }

    test("sale without btc transaction falls back to partnership amounts") {
      val props = partnershipPurchaseProps.copy(
        transaction = Transaction.PartnershipTransaction(
          details = FakePartnershipTransaction.copy(
            cryptoAmount = 0.05,
            status = PENDING,
            type = SALE
          ),
          bitcoinTransaction = null
        )
      )

      stateMachine.test(props) {
        awaitBody<TransactionDetailModel> {
          // before currency conversion

          testButtonsAndHeader(
            transaction = props.transaction,
            isPending = true,
            transactionType = Incoming,
            isLate = false
          )

          content[0].shouldBe(submittedTransactionStepper)
          content[1].shouldBe(Divider)

          // Amount Details
          with(content[2].shouldBeInstanceOf<DataList>()) {
            items[0]
              .shouldNotBeNull()
              .expect(
                title = "Submitted",
                sideText = "date-time"
              )
          }

          with(content[3].shouldBeInstanceOf<DataList>()) {
            items[0]
              .shouldNotBeNull()
              .expect(
                title = "Amount",
                sideText = "5,000,000 sats"
              )
          }
        }

        awaitBody<TransactionDetailModel> {
          // after currency conversion
          // Should use the current exchange rate

          with(content[3].shouldBeInstanceOf<DataList>()) {
            items[0]
              .shouldNotBeNull()
              .expect(
                title = "Amount",
                sideText = "$0.15",
                secondarySideText = "5,000,000 sats"
              )
          }
        }
      }
    }

    test("browser navigation opens browser url if available") {
      stateMachine.test(
        partnershipPurchaseProps.copy(
          transaction = Transaction.PartnershipTransaction(
            details = FakePartnershipTransaction.copy(
              status = PENDING
            ),
            bitcoinTransaction = null
          )
        )
      ) {
        awaitBody<TransactionDetailModel> {
          viewTransactionText.shouldBe("View in ${FakePartnershipTransaction.partnerInfo.name}")
          onViewTransaction()
        }

        inAppBrowserNavigator.onOpenCalls
          .awaitItem()
          .shouldBe(FakePartnershipTransaction.partnerTransactionUrl)
      }
    }

    test("toast displays and clipboard copies when clicking transaction id") {
      stateMachine.test(receivedProps) {
        // Skip the initial currency conversion update
        awaitBody<TransactionDetailModel>()

        // Get the transaction detail model
        awaitBody<TransactionDetailModel> {
          // Find the Transaction ID data row
          val transactionIdRow = content
            .filterIsInstance<DataList>()
            .flatMap { it.items }
            .find { it.title == "Transaction ID" }
            .shouldNotBeNull()

          // Verify the truncated ID is displayed
          transactionIdRow.sideText.shouldBe("c4f5...67be")

          // Click on the transaction ID row
          transactionIdRow.onClick?.invoke()
        }

        // Wait for the toast to appear
        awaitItem().apply {
          // Verify the clipboard received the full transaction ID
          val clipboardItem = clipboard.copiedItems.awaitItem()
          clipboardItem.shouldBeInstanceOf<ClipItem.PlainText>()
            .data.shouldBe(TX_FAKE_ID)

          // Verify the toast is displayed
          toastModel.shouldNotBeNull().apply {
            title.shouldBe("Copied")
            leadingIcon.shouldNotBeNull().iconImage.shouldBe(
              IconImage.LocalImage(Icon.SmallIconCheckFilled)
            )
          }
        }
      }
    }
  }
})

private fun TransactionDetailModel.testButtonsAndHeader(
  transaction: Transaction,
  isSpeedUpOn: Boolean = false,
  isPending: Boolean,
  transactionType: TransactionType,
  isLate: Boolean,
) {
  if (transactionType == Incoming || transactionType == UtxoConsolidation || !isPending || !isSpeedUpOn) {
    feeBumpEnabled.shouldBeFalse()
  } else {
    feeBumpEnabled.shouldBeTrue()
  }

  if (isPending) {
    formHeaderModel.shouldBe(pendingFormHeaderModel(isLate = isLate, transaction = transaction))
  } else {
    formHeaderModel.shouldBe(confirmedFormHeaderModel(transaction = transaction))
  }
}

private fun DataList.Data.expect(
  title: String,
  secondaryTitle: String? = null,
  sideText: String,
  secondarySideText: String? = null,
) {
  this.title.shouldBe(title)
  this.secondaryTitle.shouldBe(secondaryTitle)
  this.sideText.shouldBe(sideText)
  this.secondarySideText.shouldBe(secondarySideText)
}
