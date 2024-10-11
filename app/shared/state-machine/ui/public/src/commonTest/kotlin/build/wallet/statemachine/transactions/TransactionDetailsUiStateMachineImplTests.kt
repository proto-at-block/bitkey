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
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.flags.FeeBumpIsAvailableFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.*
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.time.ClockFake
import build.wallet.time.DateTimeFormatterMock
import build.wallet.time.DurationFormatterFake
import build.wallet.time.TimeZoneProviderMock
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.button.ButtonModel.Treatment.Secondary
import build.wallet.ui.model.icon.*
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
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
        accountData = ActiveKeyboxLoadedDataMock,
        transaction = BitcoinTransactionReceive,
        onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
      )

    val sentProps =
      TransactionDetailsUiProps(
        accountData = ActiveKeyboxLoadedDataMock,
        transaction = BitcoinTransactionSend,
        onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
      )

    val utxoConsolidationProps =
      TransactionDetailsUiProps(
        accountData = ActiveKeyboxLoadedDataMock,
        transaction = BitcoinTransactionUtxoConsolidation,
        onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
      )

    val pendingReceiveProps =
      TransactionDetailsUiProps(
        accountData = ActiveKeyboxLoadedDataMock,
        transaction = BitcoinTransactionReceive.copy(confirmationStatus = Pending),
        onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
      )

    val pendingSentProps =
      TransactionDetailsUiProps(
        accountData = ActiveKeyboxLoadedDataMock,
        transaction = BitcoinTransactionSend.copy(confirmationStatus = Pending),
        onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
      )

    val pendingUtxoConsolidationProps =
      TransactionDetailsUiProps(
        accountData = ActiveKeyboxLoadedDataMock,
        transaction = BitcoinTransactionUtxoConsolidation.copy(confirmationStatus = Pending),
        onClose = { inAppBrowserNavigator.onCloseCalls.add(Unit) }
      )

    val pendingSentPropsNoEstimatedConfirmationTime =
      TransactionDetailsUiProps(
        accountData = ActiveKeyboxLoadedDataMock,
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
        awaitScreenWithBody<FormBodyModel> {
          // before currency conversion

          testButtonsAndHeader(isPending = true, transactionType = Incoming, isLate = false)

          mainContentList[0]
            .shouldBeInstanceOf<DataList>()
            .items[0]
            .expect(title = "Confirmed at", sideText = "Unconfirmed")

          // Amount Details
          with(mainContentList[1].shouldBeInstanceOf<DataList>()) {
            items[0].expect(title = "Amount received", sideText = "100,000,000 sats")

            total
              .shouldNotBeNull()
              .expect(
                title = "Total",
                sideText = "100,000,000 sats",
                secondarySideText = "~$0.00"
              )
          }
        }

        awaitScreenWithBody<FormBodyModel> {
          // after currency conversion
          // Should use the current exchange rate
          with(mainContentList[1].shouldBeInstanceOf<DataList>()) {
            total
              .shouldNotBeNull()
              .secondarySideText
              .shouldBe("~$3.00")
          }
        }
      }
    }

    test("received transactions returns correct model") {
      stateMachine.test(receivedProps) {
        awaitScreenWithBody<FormBodyModel> {
          // before currency conversion

          testButtonsAndHeader(isPending = false, transactionType = Incoming, isLate = false)

          // Time Details
          mainContentList[0]
            .shouldBeInstanceOf<DataList>()
            .items[0]
            .expect(title = "Confirmed at", sideText = "confirmed-time")

          // Amount Details
          with(mainContentList[1].shouldBeInstanceOf<DataList>()) {
            items[0].expect(title = "Amount received", sideText = "100,000,000 sats")
            total
              .shouldNotBeNull()
              .expect(
                title = "Total",
                sideText = "100,000,000 sats",
                secondarySideText = "$0.00 at time confirmed"
              )
          }
        }

        awaitScreenWithBody<FormBodyModel> {
          // after currency conversion
          // Should use the current exchange rate
          with(mainContentList[1].shouldBeInstanceOf<DataList>()) {
            total
              .shouldNotBeNull()
              .secondarySideText
              .shouldBe("$3.00 at time confirmed")
          }
        }
      }
    }

    test("pending sent transaction returns correct model") {
      stateMachine.test(pendingSentProps) {
        awaitScreenWithBody<FormBodyModel> {
          // before currency conversion

          testButtonsAndHeader(isPending = true, transactionType = Outgoing, isLate = false)

          // Time Details
          with(mainContentList[0].shouldBeInstanceOf<DataList>()) {
            items[0].expect(title = "Should arrive by", sideText = "estimated-confirmation-time")
          }

          // Amount Details
          with(mainContentList[1].shouldBeInstanceOf<DataList>()) {
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
                sideText = "101,000,000 sats",
                secondarySideText = "$0.00 at time sent"
              )
          }
        }

        awaitScreenWithBody<FormBodyModel> {
          // after currency conversion
          // Should use the historical exchange rate for broadcast time
          with(mainContentList[1].shouldBeInstanceOf<DataList>()) {
            total
              .shouldNotBeNull()
              .secondarySideText
              .shouldBe("$4.04 at time sent")
          }
        }
      }
    }

    test("pending send transaction without estimate should just show unconfirmed") {
      stateMachine.test(pendingSentPropsNoEstimatedConfirmationTime) {
        awaitScreenWithBody<FormBodyModel> {
          // before currency conversion

          testButtonsAndHeader(isPending = true, transactionType = Outgoing, isLate = false)

          // Time Details
          with(mainContentList[0].shouldBeInstanceOf<DataList>()) {
            items[0].expect(title = "Confirmed at", sideText = "Unconfirmed")
          }
        }

        // after currency conversion
        awaitScreenWithBody<FormBodyModel>()
      }
    }

    test("sent transactions returns correct model") {
      stateMachine.test(sentProps) {
        awaitScreenWithBody<FormBodyModel> {
          // before currency conversion

          testButtonsAndHeader(isPending = false, transactionType = Outgoing, isLate = false)

          // Time Details
          with(mainContentList[0].shouldBeInstanceOf<DataList>()) {
            items[0].expect(title = "Confirmed at", sideText = "confirmed-time")
          }

          // Amount Details
          with(mainContentList[1].shouldBeInstanceOf<DataList>()) {
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
                sideText = "101,000,000 sats",
                secondarySideText = "$0.00 at time sent"
              )
          }
        }

        awaitScreenWithBody<FormBodyModel> {
          // after currency conversion
          // Should use the historical exchange rate
          with(mainContentList[1].shouldBeInstanceOf<DataList>()) {
            total
              .shouldNotBeNull()
              .secondarySideText
              .shouldBe("$4.04 at time sent")
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
          awaitScreenWithBody<FormBodyModel> {
            // before currency conversion

            testButtonsAndHeader(
              isSpeedUpOn = true,
              isPending = true,
              transactionType = Outgoing,
              isLate = false
            )

            // Time Details
            with(mainContentList[0].shouldBeInstanceOf<DataList>()) {
              items[0].expect(title = "Should arrive by", sideText = "estimated-confirmation-time")
            }

            // Amount Details
            with(mainContentList[1].shouldBeInstanceOf<DataList>()) {
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
                  sideText = "101,000,000 sats",
                  secondarySideText = "$0.00 at time sent"
                )
            }
          }

          awaitScreenWithBody<FormBodyModel> {
            // after currency conversion
            // Should use the historical exchange rate for broadcast time
            with(mainContentList[1].shouldBeInstanceOf<DataList>()) {
              total
                .shouldNotBeNull()
                .secondarySideText
                .shouldBe("$4.04 at time sent")
            }
          }
        }
      }

      test("correctly show late notice if current time is after promised confirmation time") {
        // Set clock to return some time that is after transaction estimated confirmation time.
        clock.now = pendingSentProps.transaction.estimatedConfirmationTime!!.plus(10.minutes)

        stateMachine.test(pendingSentProps) {
          awaitScreenWithBody<FormBodyModel> {
            testButtonsAndHeader(
              isSpeedUpOn = true,
              isPending = true,
              transactionType = Outgoing,
              isLate = true
            )

            with(mainContentList[0].shouldBeInstanceOf<DataList>()) {
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
                .icon ==
                Icon.SmallIconInformationFilled
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
          awaitScreenWithBody<FormBodyModel> {
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
                .icon ==
                Icon.SmallIconInformationFilled
              items[0]
                .explainer
                ?.iconButton
                ?.onClick
                ?.invoke()
            }
          }

          // after currency conversion
          awaitScreenWithBody<FormBodyModel>()

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
          awaitScreenWithBody<FormBodyModel> {
            testButtonsAndHeader(
              isSpeedUpOn = true,
              isPending = true,
              transactionType = Outgoing,
              isLate = true
            )
          }

          // after currency conversion
          awaitScreenWithBody<FormBodyModel> {
            clickPrimaryButton()
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
          awaitScreenWithBody<FormBodyModel> {
            testButtonsAndHeader(
              isSpeedUpOn = true,
              isPending = true,
              transactionType = Outgoing,
              isLate = true
            )
          }

          // after currency conversion
          awaitScreenWithBody<FormBodyModel> {
            clickPrimaryButton()
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
          awaitScreenWithBody<FormBodyModel> {
            testButtonsAndHeader(
              isSpeedUpOn = true,
              isPending = true,
              transactionType = Outgoing,
              isLate = true
            )
          }

          // after currency conversion
          awaitScreenWithBody<FormBodyModel> {
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
          awaitScreenWithBody<FormBodyModel> {
            // before currency conversion

            testButtonsAndHeader(
              isPending = true,
              transactionType = UtxoConsolidation,
              isLate = false
            )

            mainContentList[0]
              .shouldBeInstanceOf<DataList>()
              .items[0]
              .expect(title = "Confirmed at", sideText = "Unconfirmed")

            // Amount Details
            with(mainContentList[1].shouldBeInstanceOf<DataList>()) {
              items[0].expect(title = "UTXOs consolidated", sideText = "2 → 1")
              items[1].expect(
                title = "Consolidation cost",
                sideText = "10,000,000 sats",
                secondarySideText = "~$0.00"
              )
              total.shouldBeNull()
            }
          }

          awaitScreenWithBody<FormBodyModel> {
            // after currency conversion
            // Should use the current exchange rate
            with(mainContentList[1].shouldBeInstanceOf<DataList>()) {
              items[1].expect(
                title = "Consolidation cost",
                sideText = "10,000,000 sats",
                secondarySideText = "~$0.30"
              )
              total.shouldBeNull()
            }
          }
        }
      }

      test("utxo consolidation transaction returns correct model") {
        stateMachine.test(utxoConsolidationProps) {
          awaitScreenWithBody<FormBodyModel> {
            // before currency conversion

            testButtonsAndHeader(
              isPending = false,
              transactionType = UtxoConsolidation,
              isLate = false
            )

            // Time Details
            mainContentList[0]
              .shouldBeInstanceOf<DataList>()
              .items[0]
              .expect(title = "Confirmed at", sideText = "confirmed-time")

            // Amount Details
            with(mainContentList[1].shouldBeInstanceOf<DataList>()) {
              items[0].expect(title = "UTXOs consolidated", sideText = "2 → 1")
              items[1].expect(
                title = "Consolidation cost",
                sideText = "10,000,000 sats",
                secondarySideText = "$0.00 at time confirmed"
              )
              total.shouldBeNull()
            }
          }

          awaitScreenWithBody<FormBodyModel> {
            // after currency conversion
            // Should use the current exchange rate
            with(mainContentList[1].shouldBeInstanceOf<DataList>()) {
              items[1].expect(
                title = "Consolidation cost",
                sideText = "10,000,000 sats",
                secondarySideText = "$0.30 at time confirmed"
              )
              total.shouldBeNull()
            }
          }
        }
      }
    }
  })

private fun FormBodyModel.testButtonsAndHeader(
  isSpeedUpOn: Boolean = false,
  isPending: Boolean,
  transactionType: TransactionType,
  isLate: Boolean,
) {
  if (transactionType == Incoming || transactionType == UtxoConsolidation || !isPending || !isSpeedUpOn) {
    primaryButton
      .shouldNotBeNull()
      .expect(SmallIconArrowUpRight, "View Transaction", Primary, Footer)
  } else {
    primaryButton.shouldNotBeNull().expect(SmallIconLightning, "Speed Up", Secondary, Footer)
    secondaryButton
      .shouldNotBeNull()
      .expect(SmallIconArrowUpRight, "View Transaction", Primary, Footer)
  }

  header
    .shouldNotBeNull()
    .expect(
      iconModel = if (isPending) {
        if (isLate) {
          IconModel(
            icon = LargeIconWarningFilled,
            iconSize = IconSize.Avatar,
            iconTint = IconTint.Primary
          )
        } else {
          IconModel(
            iconImage = IconImage.Loader,
            iconSize = IconSize.Large,
            iconBackgroundType = IconBackgroundType.Circle(circleSize = IconSize.Avatar)
          )
        }
      } else {
        when (transactionType) {
          Incoming -> IncomingTransactionIconModel
          Outgoing -> OutgoingTransactionIconModel
          UtxoConsolidation -> UtxoConsolidationTransactionIconModel
        }
      },
      headline =
        if (isPending) {
          if (isLate) {
            "Transaction delayed"
          } else {
            when (transactionType) {
              Incoming, Outgoing -> "Transaction pending"
              UtxoConsolidation -> "Consolidation pending"
            }
          }
        } else {
          when (transactionType) {
            Incoming -> "Transaction received"
            Outgoing -> "Transaction sent"
            UtxoConsolidation -> "UTXO Consolidation"
          }
        },
      subline = "bc1z w508 d6qe jxtd g4y5 r3za rvar yvax xpcs"
    )
}

private fun ButtonModel.expect(
  icon: Icon,
  text: String,
  treatment: Treatment,
  size: Size,
) {
  this.leadingIcon.shouldBe(icon)
  this.text.shouldBe(text)
  this.treatment.shouldBe(treatment)
  this.size.shouldBe(size)
}

private fun FormHeaderModel.expect(
  iconModel: IconModel,
  headline: String,
  subline: String,
) {
  this.iconModel.shouldBe(iconModel)
  this.headline.shouldBe(headline)
  this.sublineModel
    .shouldNotBeNull()
    .string
    .shouldBe(subline)
}

private fun DataList.Data.expect(
  title: String,
  sideText: String,
  secondarySideText: String? = null,
) {
  this.title.shouldBe(title)
  this.sideText.shouldBe(sideText)
  this.secondarySideText.shouldBe(secondarySideText)
}
