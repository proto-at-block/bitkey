package build.wallet.statemachine.transactions

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action
import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.explorer.BitcoinExplorerMock
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimatorMock
import build.wallet.bitcoin.transactions.*
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.*
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.flags.FeeBumpIsAvailableFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.Icon.*
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.time.ClockFake
import build.wallet.time.DateTimeFormatterMock
import build.wallet.time.DurationFormatterFake
import build.wallet.time.TimeZoneProviderMock
import build.wallet.ui.model.icon.*
import com.github.michaelbull.result.Err
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

class TransactionDetailsUiStateMachineImplTests :
  FunSpec({

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

    val feeBumpEnabledFeatureFlag =
      FeeBumpIsAvailableFeatureFlag(
        featureFlagDao = FeatureFlagDaoMock()
      )
    val bitcoinTransactionBumpabilityChecker =
      BitcoinTransactionBumpabilityCheckerFake(isBumpable = false)
    val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
    val feeBumpConfirmationUiStateMachine = object : FeeBumpConfirmationUiStateMachine,
      ScreenStateMachineMock<FeeBumpConfirmationProps>("fee-bump-confirmation") {}
    val spendingWallet = SpendingWalletMock(turbines::create)
    val transactionsService = TransactionsServiceFake().apply {
      this.spendingWallet.value = spendingWallet
    }

    val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)

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
        feeBumpEnabled = feeBumpEnabledFeatureFlag,
        bitcoinTransactionBumpabilityChecker = bitcoinTransactionBumpabilityChecker,
        fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
        feeBumpConfirmationUiStateMachine = feeBumpConfirmationUiStateMachine,
        feeRateEstimator = BitcoinFeeRateEstimatorMock(),
        inAppBrowserNavigator = inAppBrowserNavigator,
        transactionsService = transactionsService
      )

    val receivedProps =
      TransactionDetailsUiProps(
        account = FullAccountMock,
        transaction = BitcoinTransactionReceive,
        onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
      )

    val sentProps =
      TransactionDetailsUiProps(
        account = FullAccountMock,
        transaction = BitcoinTransactionSend,
        onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
      )

    val utxoConsolidationProps =
      TransactionDetailsUiProps(
        account = FullAccountMock,
        transaction = BitcoinTransactionUtxoConsolidation,
        onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
      )

    val pendingReceiveProps =
      TransactionDetailsUiProps(
        account = FullAccountMock,
        transaction = BitcoinTransactionReceive.copy(confirmationStatus = Pending),
        onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
      )

    val pendingSentProps =
      TransactionDetailsUiProps(
        account = FullAccountMock,
        transaction = BitcoinTransactionSend.copy(confirmationStatus = Pending),
        onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
      )

    val pendingUtxoConsolidationProps =
      TransactionDetailsUiProps(
        account = FullAccountMock,
        transaction = BitcoinTransactionUtxoConsolidation.copy(
          confirmationStatus = Pending
        ),
        onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
      )

    val pendingSentPropsNoEstimatedConfirmationTime =
      TransactionDetailsUiProps(
        account = FullAccountMock,
        transaction =
          BitcoinTransactionSend.copy(
            confirmationStatus = Pending,
            estimatedConfirmationTime = null
          ),
        onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
      )

    beforeTest {
      transactionsService.reset()
      transactionsService.spendingWallet.value = spendingWallet
      bitcoinTransactionBumpabilityChecker.isBumpable = false
    }

    test("pending receive transaction returns correct model") {
      stateMachine.test(pendingReceiveProps) {
        awaitScreenWithBody<TransactionDetailModel> {
          // before currency conversion

          testButtonsAndHeader(isPending = true, transactionType = Incoming, isLate = false)

          // Amount Details
          with(content[0].shouldBeInstanceOf<DataList>()) {
            total
              .shouldNotBeNull()
              .expect(
                title = "Amount receiving",
                sideText = "~$0.00",
                secondarySideText = "100,000,000 sats"
              )
          }
        }

        awaitScreenWithBody<TransactionDetailModel> {
          // after currency conversion
          // Should use the current exchange rate

          with(content[0].shouldBeInstanceOf<DataList>()) {
            total
              .shouldNotBeNull()
              .expect(
                title = "Amount receiving",
                sideText = "~$3.00",
                secondarySideText = "100,000,000 sats"
              )
          }
        }
      }
    }

    test("received transactions returns correct model") {
      stateMachine.test(receivedProps) {
        awaitScreenWithBody<TransactionDetailModel> {
          // before currency conversion

          testButtonsAndHeader(isPending = false, transactionType = Incoming, isLate = false)

          // Time Details
          content[0]
            .shouldBeInstanceOf<DataList>()
            .items[0]
            .expect(title = "Confirmed at", sideText = "confirmed-time")

          // Amount Details
          with(content[1].shouldBeInstanceOf<DataList>()) {
            total
              .shouldNotBeNull()
              .expect(
                title = "Amount received",
                secondaryTitle = "At time confirmed",
                sideText = "$0.00",
                secondarySideText = "100,000,000 sats"
              )
          }
        }

        awaitScreenWithBody<TransactionDetailModel> {
          // after currency conversion
          // Should use the current exchange rate
          with(content[1].shouldBeInstanceOf<DataList>()) {
            total
              .shouldNotBeNull()
              .expect(
                title = "Amount received",
                secondaryTitle = "At time confirmed",
                sideText = "$3.00",
                secondarySideText = "100,000,000 sats"
              )
          }
        }
      }
    }

    test("pending sent transaction returns correct model") {
      stateMachine.test(pendingSentProps) {
        awaitScreenWithBody<TransactionDetailModel> {
          // before currency conversion

          testButtonsAndHeader(isPending = true, transactionType = Outgoing, isLate = false)

          // Time Details
          with(content[0].shouldBeInstanceOf<DataList>()) {
            items[0].expect(title = "Should arrive by", sideText = "estimated-confirmation-time")
          }

          // Amount Details
          with(content[1].shouldBeInstanceOf<DataList>()) {
            items[0].expect(
              title = "Recipient receives",
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
                secondaryTitle = "At time sent",
                sideText = "$0.00",
                secondarySideText = "101,000,000 sats"
              )
          }
        }

        awaitScreenWithBody<TransactionDetailModel> {
          // after currency conversion
          // Should use the historical exchange rate for broadcast time
          with(content[1].shouldBeInstanceOf<DataList>()) {
            items[0].expect(
              title = "Recipient receives",
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
                secondaryTitle = "At time sent",
                sideText = "$4.04",
                secondarySideText = "101,000,000 sats"
              )
          }
        }
      }
    }

    test("pending send transaction without estimate does not show confirmation row") {
      stateMachine.test(pendingSentPropsNoEstimatedConfirmationTime) {
        awaitScreenWithBody<TransactionDetailModel> {
          // before currency conversion

          testButtonsAndHeader(isPending = true, transactionType = Outgoing, isLate = false)

          // Time Details
          with(content[0].shouldBeInstanceOf<DataList>()) {
            items[0].expect(title = "Recipient receives", sideText = "100,000,000 sats")
          }
        }

        // after currency conversion
        awaitScreenWithBody<TransactionDetailModel>()
      }
    }

    test("sent transactions returns correct model") {
      stateMachine.test(sentProps) {
        awaitScreenWithBody<TransactionDetailModel> {
          // before currency conversion

          testButtonsAndHeader(isPending = false, transactionType = Outgoing, isLate = false)

          // Time Details
          with(content[0].shouldBeInstanceOf<DataList>()) {
            items[0].expect(title = "Confirmed at", sideText = "confirmed-time")
          }

          // Amount Details
          with(content[1].shouldBeInstanceOf<DataList>()) {
            items[0].expect(
              title = "Recipient received",
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
                secondaryTitle = "At time confirmed",
                sideText = "$0.00",
                secondarySideText = "101,000,000 sats"
              )
          }
        }

        awaitScreenWithBody<TransactionDetailModel> {
          // after currency conversion
          // Should use the historical exchange rate
          with(content[1].shouldBeInstanceOf<DataList>()) {
            items[0].expect(
              title = "Recipient received",
              sideText = "$4.00",
              secondarySideText = "100,000,000 sats"
            )
            items[1].expect(
              title = "Network fees",
              sideText = "$0.03",
              secondarySideText = "1,000,000 sats"
            )
            total
              .shouldNotBeNull()
              .expect(
                title = "Total",
                secondaryTitle = "At time confirmed",
                sideText = "$4.04",
                secondarySideText = "101,000,000 sats"
              )
          }
        }
      }
    }

    test("onClose is called") {
      stateMachine.test(pendingReceiveProps) {
        awaitScreenWithBody<FormBodyModel> {
          onBack?.invoke()
        }
        inAppBrowserNavigator.onCloseCalls.awaitItem().shouldBe(Unit)

        cancelAndIgnoreRemainingEvents()
      }
    }

    test("browser navigation opens on primary button click") {
      stateMachine.test(pendingReceiveProps) {
        awaitScreenWithBody<FormBodyModel>()

        awaitScreenWithBody<FormBodyModel> {
          clickPrimaryButton()
        }

        inAppBrowserNavigator.onOpenCalls
          .awaitItem()
          .shouldBe(
            "https://mempool.space/tx/4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b"
          )
      }
    }

    context("Speed up feature flag is on") {
      beforeTest {
        feeBumpEnabledFeatureFlag.setFlagValue(true)
        bitcoinTransactionBumpabilityChecker.isBumpable = true
      }

      test("pending sent transaction returns correct model") {
        stateMachine.test(pendingSentProps) {
          awaitScreenWithBody<TransactionDetailModel> {
            // before currency conversion

            testButtonsAndHeader(
              isSpeedUpOn = true,
              isPending = true,
              transactionType = Outgoing,
              isLate = false
            )

            // Time Details
            with(content[0].shouldBeInstanceOf<DataList>()) {
              items[0].expect(title = "Should arrive by", sideText = "estimated-confirmation-time")
            }

            // Amount Details
            with(content[1].shouldBeInstanceOf<DataList>()) {
              items[0].expect(
                title = "Recipient receives",
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
                  secondaryTitle = "At time sent",
                  sideText = "$0.00",
                  secondarySideText = "101,000,000 sats"
                )
            }
          }

          awaitScreenWithBody<TransactionDetailModel> {
            // after currency conversion
            // Should use the historical exchange rate for broadcast time
            with(content[1].shouldBeInstanceOf<DataList>()) {
              items[0].expect(
                title = "Recipient receives",
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
                  secondaryTitle = "At time sent",
                  sideText = "$4.04",
                  secondarySideText = "101,000,000 sats"
                )
            }
          }
        }
      }

      test("correctly show late notice if current time is after promised confirmation time") {
        // Set clock to return some time that is after transaction estimated confirmation time.
        clock.now = pendingSentProps.transaction.estimatedConfirmationTime!!.plus(10.minutes)

        stateMachine.test(pendingSentProps) {
          awaitScreenWithBody<TransactionDetailModel> {
            testButtonsAndHeader(
              isSpeedUpOn = true,
              isPending = true,
              transactionType = Outgoing,
              isLate = true
            )

            with(content[0].shouldBeInstanceOf<DataList>()) {
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
          awaitScreenWithBody<FormBodyModel>()
        }
      }

      test("tapping explainer info icon should open education sheet") {
        // Set clock to return some time that is after transaction estimated confirmation time.
        clock.now = pendingSentProps.transaction.estimatedConfirmationTime!!.plus(10.minutes)

        stateMachine.test(pendingSentProps) {
          awaitScreenWithBody<TransactionDetailModel> {
            testButtonsAndHeader(
              isSpeedUpOn = true,
              isPending = true,
              transactionType = Outgoing,
              isLate = true
            )

            with(mainContentList[0].shouldBeInstanceOf<DataList>()) {
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

          // after currency conversion
          awaitScreenWithBody<TransactionDetailModel>()

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
          awaitScreenWithBody<FormBodyModel>()
        }
      }

      test("tapping speed up should open the fee bump flow") {
        stateMachine.test(pendingSentProps) {
          awaitScreenWithBody<TransactionDetailModel> {
            testButtonsAndHeader(
              isSpeedUpOn = true,
              isPending = true,
              transactionType = Outgoing,
              isLate = true
            )
          }

          // after currency conversion
          awaitScreenWithBody<TransactionDetailModel> {
            onSpeedUpTransaction()
          }

          // Ensure we log analytics event
          eventTracker.eventCalls.awaitItem()

          // loading the fee rates and fetching wallet
          awaitScreenWithBody<FormBodyModel>()

          // Show fee bump flow
          awaitScreenWithBodyModelMock<FeeBumpConfirmationProps>()
        }
      }

      test("tapping speed up with insufficient balance to bump fee should show error screen") {
        spendingWallet.createSignedPsbtResult = Err(BdkError.InsufficientFunds(null, null))
        stateMachine.test(pendingSentProps) {
          awaitScreenWithBody<TransactionDetailModel> {
            testButtonsAndHeader(
              isSpeedUpOn = true,
              isPending = true,
              transactionType = Outgoing,
              isLate = true
            )
          }

          // after currency conversion
          awaitScreenWithBody<TransactionDetailModel> {
            onSpeedUpTransaction()
          }

          // loading the fee rates and fetching wallet
          awaitScreenWithBody<FormBodyModel>()

          // failed to launch fee bump flow from insufficient funds
          awaitScreenWithBody<FormBodyModel> {
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
        spendingWallet.createSignedPsbtResult = Err(BdkError.FeeRateTooLow(null, null))
        stateMachine.test(pendingSentProps) {
          awaitScreenWithBody<TransactionDetailModel> {
            testButtonsAndHeader(
              isSpeedUpOn = true,
              isPending = true,
              transactionType = Outgoing,
              isLate = true
            )
          }

          // after currency conversion
          awaitScreenWithBody<TransactionDetailModel> {
            clickPrimaryButton()
          }

          // loading the fee rates and fetching wallet
          awaitScreenWithBody<FormBodyModel>()

          // failed to launch fee bump flow from insufficient funds
          awaitScreenWithBody<FormBodyModel> {
            header.shouldNotBeNull()
              .apply {
                headline
                  .shouldBe("We couldn’t speed up this transaction")
                sublineModel
                  .shouldNotBeNull()
                  .string
                  .shouldBe("The current fee rate is too low. Please try again later.")
              }
          }

          // Ensure we log analytics event
          eventTracker.eventCalls
            .awaitItem()
            .shouldBe(TrackedAction(Action.ACTION_APP_ATTEMPT_SPEED_UP_TRANSACTION))
        }
      }
    }

    context("utxo consolidation") {
      test("pending utxo consolidation transaction returns correct model") {
        stateMachine.test(pendingUtxoConsolidationProps) {
          awaitScreenWithBody<TransactionDetailModel> {
            // before currency conversion

            testButtonsAndHeader(
              isPending = true,
              transactionType = UtxoConsolidation,
              isLate = false
            )

            // Amount Details
            with(content[0].shouldBeInstanceOf<DataList>()) {
              items[0].expect(title = "UTXOs consolidated", sideText = "2 → 1")
              items[1].expect(
                title = "Consolidation cost",
                sideText = "10,000,000 sats"
              )
              total.shouldBeNull()
            }
          }

          awaitScreenWithBody<TransactionDetailModel> {
            // after currency conversion
            // Should use the current exchange rate
            with(content[0].shouldBeInstanceOf<DataList>()) {
              items[1].expect(
                title = "Consolidation cost",
                sideText = "~$0.30",
                secondarySideText = "10,000,000 sats"
              )
              total.shouldBeNull()
            }
          }
        }
      }

      test("utxo consolidation transaction returns correct model") {
        stateMachine.test(utxoConsolidationProps) {
          awaitScreenWithBody<TransactionDetailModel> {
            // before currency conversion

            testButtonsAndHeader(
              isPending = false,
              transactionType = UtxoConsolidation,
              isLate = false
            )

            // Time Details
            content[0]
              .shouldBeInstanceOf<DataList>()
              .items[0]
              .expect(title = "Confirmed at", sideText = "confirmed-time")

            // Amount Details
            with(content[1].shouldBeInstanceOf<DataList>()) {
              items[0].expect(title = "UTXOs consolidated", sideText = "2 → 1")
              items[1].expect(
                title = "Consolidation cost",
                secondaryTitle = "At time confirmed",
                sideText = "10,000,000 sats"
              )
              total.shouldBeNull()
            }
          }

          awaitScreenWithBody<TransactionDetailModel> {
            // after currency conversion
            // Should use the current exchange rate
            with(content[1].shouldBeInstanceOf<DataList>()) {
              items[1].expect(
                title = "Consolidation cost",
                secondaryTitle = "At time confirmed",
                sideText = "$0.30",
                secondarySideText = "10,000,000 sats"
              )
              total.shouldBeNull()
            }
          }
        }
      }
    }
  })

private fun TransactionDetailModel.testButtonsAndHeader(
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

  txStatusModel.transactionType.shouldBe(transactionType)
  txStatusModel.recipientAddress.shouldBe("bc1z w508 d6qe jxtd g4y5 r3za rvar yvax xpcs")
  if (isPending) {
    txStatusModel.shouldBeTypeOf<TxStatusModel.Pending>()
      .isLate.shouldBe(isLate)
  } else {
    txStatusModel.shouldBeTypeOf<TxStatusModel.Confirmed>()
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
