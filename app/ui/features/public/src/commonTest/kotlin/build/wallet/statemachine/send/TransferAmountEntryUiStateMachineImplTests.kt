package build.wallet.statemachine.send

import app.cash.turbine.plusAssign
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.availability.AppFunctionalityStatus.LimitedFunctionality
import build.wallet.availability.F8eUnreachable
import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.TransactionsDataMock
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.DesignSystemUpdatesFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.limit.DailySpendingLimitStatus
import build.wallet.limit.MobilePayServiceMock
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import build.wallet.money.currency.USD
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.money.calculator.MoneyCalculatorModel
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiProps
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiStateMachine
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.send.amountentry.TransferCardUiProps
import build.wallet.statemachine.send.amountentry.TransferCardUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.matchers.shouldBeDisabled
import build.wallet.statemachine.ui.matchers.shouldHaveText
import build.wallet.statemachine.ui.robots.click
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.model.button.ButtonModel
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class TransferAmountEntryUiStateMachineImplTests : FunSpec({
  val conversionRate = 3.3333
  val bitcoinBalance = BitcoinMoney.btc(5.0)

  // Assuming primary currency is USD and secondary is BTC
  val balancePrimaryAmount = FiatMoney.usd(16.67)
  val balanceSecondaryAmount = bitcoinBalance

  val defaultSecondaryAmount = BitcoinMoney.sats(1000)
  val defaultMoneyCalculatorModel =
    MoneyCalculatorModel(
      primaryAmount = FiatMoney.usd(1.0),
      secondaryAmount = defaultSecondaryAmount,
      amountModel =
        MoneyAmountEntryModel(
          primaryAmount = "$1",
          primaryAmountGhostedSubstringRange = null,
          secondaryAmount = "1000 sats"
        ),
      keypadModel = KeypadModel(showDecimal = true, onButtonPress = {})
    )
  val moneyCalculatorUiStateMachine =
    object : MoneyCalculatorUiStateMachine,
      StateMachineMock<MoneyCalculatorUiProps, MoneyCalculatorModel>(
        defaultMoneyCalculatorModel
      ) {}

  val transferCardUiStateMachine =
    object : TransferCardUiStateMachine,
      StateMachineMock<TransferCardUiProps, CardModel?>(
        null
      ) {}
  val mobilePayService = MobilePayServiceMock(turbines::create)
  val designSystemUpdatesFeatureFlag = DesignSystemUpdatesFeatureFlag(FeatureFlagDaoFake())
  val appFunctionalityService = AppFunctionalityServiceFake()

  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val bitcoinWalletService = BitcoinWalletServiceFake()
  val stateMachine = TransferAmountEntryUiStateMachineImpl(
    currencyConverter = CurrencyConverterFake(conversionRate = 3.3333),
    moneyCalculatorUiStateMachine = moneyCalculatorUiStateMachine,
    moneyDisplayFormatter = MoneyDisplayFormatterFake,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
    bitcoinWalletService = bitcoinWalletService,
    designSystemUpdatesFeatureFlag = designSystemUpdatesFeatureFlag,
    transferCardUiStateMachine = transferCardUiStateMachine,
    appFunctionalityService = appFunctionalityService
  )

  val onContinueClickCalls = turbines.create<ContinueTransferParams>("onContinueClick calls")

  val props =
    TransferAmountEntryUiProps(
      onBack = {},
      initialAmount = FiatMoney.usd(1.0),
      onContinueClick = { onContinueClickCalls += it },
      exchangeRates = emptyImmutableList(),
      flow = TransferAmountEntryUiProps.Flow.Send(allowSendAll = true)
    )
  val sellProps =
    props.copy(
      flow = TransferAmountEntryUiProps.Flow.Sell(
        minAmount = BitcoinMoney.btc(0.3),
        maxAmount = BitcoinMoney.btc(0.9)
      )
    )

  beforeTest {
    designSystemUpdatesFeatureFlag.setFlagValue(false)
    mobilePayService.reset()
    appFunctionalityService.reset()
    mobilePayService.status = DailySpendingLimitStatus.MobilePayAvailable
    bitcoinWalletService.reset()

    bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
      balance = BitcoinBalanceFake(confirmed = bitcoinBalance)
    )
  }

  beforeTest {
    moneyCalculatorUiStateMachine.emitModel(defaultMoneyCalculatorModel)
  }

  test("initial balance amount and balance update") {
    stateMachine.test(props) {
      awaitBody<TransferAmountBodyModel> {
        toolbar.middleAccessory.shouldNotBeNull().subtitle.shouldBe("\$16.67 available")
        moneyCalculatorUiStateMachine.props.inputAmountCurrency.shouldBe(USD)
        moneyCalculatorUiStateMachine.props.secondaryDisplayAmountCurrency
          .shouldBe(BTC)
      }

      bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
        balance = BitcoinBalanceFake(confirmed = BitcoinMoney.btc(10.0))
      )

      // After balance update – now with amount from balance provider
      awaitBody<TransferAmountBodyModel> {
        toolbar.middleAccessory.shouldNotBeNull().subtitle.shouldBe("\$33.33 available")
      }
    }
  }

  test("initial amount in btc") {
    stateMachine.test(props.copy(initialAmount = BitcoinMoney.btc(1.0))) {
      awaitBody<TransferAmountBodyModel> {
        toolbar.middleAccessory.shouldNotBeNull().subtitle
          .shouldBe("500,000,000 sats available")
        moneyCalculatorUiStateMachine.props.inputAmountCurrency.shouldBe(BTC)
        moneyCalculatorUiStateMachine.props.secondaryDisplayAmountCurrency
          .shouldBe(USD)
      }
    }
  }

  test("entered amount at exactly balance") {
    stateMachine.test(props) {
      awaitBody<TransferAmountBodyModel> {
        toolbar.middleAccessory.shouldNotBeNull().subtitle.shouldBe("\$16.67 available")
      }

      moneyCalculatorUiStateMachine.emitModel(
        defaultMoneyCalculatorModel.copy(
          primaryAmount = balancePrimaryAmount,
          secondaryAmount = balanceSecondaryAmount
        )
      )

      awaitBody<TransferAmountBodyModel>()
    }
  }

  test("entered amount above balance in fiat") {
    stateMachine.test(props) {
      awaitBody<TransferAmountBodyModel> {
        toolbar.middleAccessory.shouldNotBeNull().subtitle.shouldBe("\$16.67 available")
      }

      val primaryAmountAboveBalance = balancePrimaryAmount + FiatMoney.usd(0.1)
      moneyCalculatorUiStateMachine.emitModel(
        defaultMoneyCalculatorModel.copy(
          primaryAmount = primaryAmountAboveBalance,
          secondaryAmount =
            BitcoinMoney(
              currency = BTC,
              primaryAmountAboveBalance.value.divide(
                conversionRate.toBigDecimal(),
                BTC.decimalMode()
              )
            )
        )
      )

      awaitBody<TransferAmountBodyModel>()
    }
  }

  test("sell flow shows allowed range and keeps amount enabled when amount is valid") {
    val validSellAmountModel =
      defaultMoneyCalculatorModel.copy(
        primaryAmount = FiatMoney.usd(2.0),
        secondaryAmount = BitcoinMoney.btc(0.6),
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$2.00",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "60,000,000 sats"
          )
      )
    moneyCalculatorUiStateMachine.emitModel(validSellAmountModel)

    stateMachine.test(sellProps) {
      awaitBody<TransferAmountBodyModel> {
        amountDisabled.shouldBeFalse()

        onSwapCurrencyClick.shouldNotBeNull()
        primaryButton.isEnabled.shouldBeTrue()
      }
    }
  }

  test("sell flow shows allowed range and keeps amount enabled when amount is below minimum") {
    moneyCalculatorUiStateMachine.emitModel(
      defaultMoneyCalculatorModel.copy(
        primaryAmount = FiatMoney.usd(0.5),
        secondaryAmount = BitcoinMoney.btc(0.15),
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$0.50",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "15,000,000 sats"
          )
      )
    )

    stateMachine.test(sellProps) {
      awaitBody<TransferAmountBodyModel> {
        amountDisabled.shouldBeFalse()

        amountModel.secondaryAmount.shouldBe("Minimum sell amount is $1.00")
        amountContextLineTreatment.shouldBe(LabelTreatment.Destructive)
        onSwapCurrencyClick.shouldBeNull()
        primaryButton.shouldBeDisabled()
      }
    }
  }

  test("sell flow shows allowed range and keeps amount enabled when amount is above maximum") {
    moneyCalculatorUiStateMachine.emitModel(
      defaultMoneyCalculatorModel.copy(
        primaryAmount = FiatMoney.usd(4.0),
        secondaryAmount = BitcoinMoney.btc(1.2),
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$4.00",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "120,000,000 sats"
          )
      )
    )

    stateMachine.test(sellProps) {
      awaitBody<TransferAmountBodyModel> {
        amountDisabled.shouldBeFalse()

        amountModel.secondaryAmount.shouldBe("Maximum sell amount is $3.00")
        amountContextLineTreatment.shouldBe(LabelTreatment.Destructive)
        onSwapCurrencyClick.shouldBeNull()
        primaryButton.shouldBeDisabled()
      }
    }
  }

  test("sell flow shows balance message and keeps amount enabled when amount exceeds available balance") {
    moneyCalculatorUiStateMachine.emitModel(
      defaultMoneyCalculatorModel.copy(
        primaryAmount = FiatMoney.usd(20.0),
        secondaryAmount = BitcoinMoney.btc(6.0),
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$20.00",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "600,000,000 sats"
          )
      )
    )

    stateMachine.test(sellProps) {
      awaitBody<TransferAmountBodyModel> {
        amountDisabled.shouldBeFalse()

        amountModel.secondaryAmount.shouldBe("Amount exceeds available balance")
        amountContextLineTreatment.shouldBe(LabelTreatment.Destructive)
        onSwapCurrencyClick.shouldBeNull()
        shouldTriggerContextualErrorFeedback.shouldBeTrue()
        primaryButton.shouldBeDisabled()
      }
    }
  }

  test("sell flow shows insufficient balance message when balance is below minimum sell amount") {
    bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
      balance = BitcoinBalanceFake(confirmed = BitcoinMoney.btc(0.1))
    )

    moneyCalculatorUiStateMachine.emitModel(
      defaultMoneyCalculatorModel.copy(
        primaryAmount = FiatMoney.usd(0.1),
        secondaryAmount = BitcoinMoney.btc(0.03),
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$0.10",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "3,000,000 sats"
          )
      )
    )

    stateMachine.test(sellProps) {
      awaitBody<TransferAmountBodyModel> {
        amountDisabled.shouldBeFalse()

        amountModel.secondaryAmount.shouldBe("Minimum sell amount is $1.00")
        amountContextLineTreatment.shouldBe(LabelTreatment.Destructive)
        primaryButton.shouldBeDisabled()
      }
    }
  }

  test("sell flow shows no error at zero entry when balance is below minimum sell amount") {
    bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
      balance = BitcoinBalanceFake(confirmed = BitcoinMoney.btc(0.1))
    )

    moneyCalculatorUiStateMachine.emitModel(
      defaultMoneyCalculatorModel.copy(
        primaryAmount = FiatMoney.zeroUsd(),
        secondaryAmount = BitcoinMoney.zero(),
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$0",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "0 sats"
          )
      )
    )

    stateMachine.test(sellProps) {
      awaitBody<TransferAmountBodyModel> {
        amountDisabled.shouldBeFalse()

        amountModel.secondaryAmount.shouldBe("0 sats")
        amountContextLineTreatment.shouldBe(LabelTreatment.Secondary)
        primaryButton.shouldBeDisabled()
      }
    }
  }

  test("sell flow keeps a standard continue button and no smart bar when hardware would be required") {
    mobilePayService.status = DailySpendingLimitStatus.RequiresHardware
    moneyCalculatorUiStateMachine.emitModel(
      defaultMoneyCalculatorModel.copy(
        primaryAmount = FiatMoney.usd(2.0),
        secondaryAmount = BitcoinMoney.btc(0.6),
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$2.00",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "60,000,000 sats"
          )
      )
    )

    stateMachine.test(sellProps) {
      awaitBody<TransferAmountBodyModel> {
        cardModel.shouldBeNull()
        useSmartBar.shouldBeFalse()
        primaryButton.treatment.shouldBe(ButtonModel.Treatment.Primary)
        primaryButton.leadingIcon.shouldBeNull()
        onSwapCurrencyClick.shouldNotBeNull()
        primaryButton.isEnabled.shouldBeTrue()
      }
    }
  }

  test("send flow shows swap control before a valid amount is entered") {
    moneyCalculatorUiStateMachine.emitModel(
      defaultMoneyCalculatorModel.copy(
        primaryAmount = FiatMoney.zeroUsd(),
        secondaryAmount = BitcoinMoney.zero(),
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$0",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "0 sats"
          )
      )
    )

    stateMachine.test(props.copy(initialAmount = FiatMoney.zeroUsd())) {
      awaitBody<TransferAmountBodyModel> {
        onSwapCurrencyClick.shouldNotBeNull()
        primaryButton.shouldBeDisabled()
      }
    }
  }

  context("Send Max is Available") {
    test("Should show smart bar and keep amount hero enabled if spending above balance") {
      designSystemUpdatesFeatureFlag.setFlagValue(true)
      val primaryAmountAboveBalance = balancePrimaryAmount + FiatMoney.usd(0.1)
      val secondaryAmountAboveBalance = BitcoinMoney(
        currency = BTC,
        primaryAmountAboveBalance.value.divide(
          conversionRate.toBigDecimal(),
          BTC.decimalMode()
        )
      )
      moneyCalculatorUiStateMachine.emitModel(
        defaultMoneyCalculatorModel.copy(
          primaryAmount = primaryAmountAboveBalance,
          secondaryAmount = secondaryAmountAboveBalance
        )
      )

      stateMachine.test(props) {
        awaitBody<TransferAmountBodyModel> {
          amountDisabled.shouldBeFalse()
          useSmartBar.shouldBeTrue()
  
          amountModel.secondaryAmount.shouldBe("Amount exceeds available balance")
          amountContextLineTreatment.shouldBe(LabelTreatment.Destructive)
          onSwapCurrencyClick.shouldBeNull()
          shouldTriggerContextualErrorFeedback.shouldBeTrue()
          primaryButton.shouldBeDisabled()
        }

        transferCardUiStateMachine.props.transferAmountState
          .shouldBe(TransferAmountUiState.ValidAmountEnteredUiState.AmountEqualOrAboveBalanceUiState)
      }
    }

    test("Should show approval required") {
      val primaryAmountBelowBalance = balancePrimaryAmount - FiatMoney.usd(0.1)
      val secondaryAmountBelowBalance = BitcoinMoney(
        currency = BTC,
        primaryAmountBelowBalance.value.divide(
          conversionRate.toBigDecimal(),
          BTC.decimalMode()
        )
      )
      moneyCalculatorUiStateMachine.emitModel(
        defaultMoneyCalculatorModel.copy(
          primaryAmount = primaryAmountBelowBalance,
          secondaryAmount = secondaryAmountBelowBalance
        )
      )

      stateMachine.test(props) {
        awaitBody<TransferAmountBodyModel> {
          toolbar.middleAccessory.shouldNotBeNull().subtitle.shouldBe("\$16.67 available")
          useSmartBar.shouldBeFalse()
        }
      }

      stateMachine.test(props) {
        awaitBody<TransferAmountBodyModel> {
          amountDisabled.shouldBeFalse()
          useSmartBar.shouldBeFalse()
        }
      }
    }

    test("Should not show smart bar when user has no balance") {
      bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
        balance = BitcoinBalanceFake(confirmed = BitcoinMoney.btc(0.0))
      )

      val zeroBalanceProps = props.copy(initialAmount = FiatMoney.usd(0.0))
      moneyCalculatorUiStateMachine.emitModel(
        defaultMoneyCalculatorModel.copy(
          primaryAmount = FiatMoney.zero(USD),
          secondaryAmount = BitcoinMoney.zero(),
          amountModel =
            MoneyAmountEntryModel(
              primaryAmount = "$0",
              primaryAmountGhostedSubstringRange = null,
              secondaryAmount = "0 sats"
            )
        )
      )

      stateMachine.testWithVirtualTime(zeroBalanceProps) {
        awaitBody<TransferAmountBodyModel> {
          amountDisabled.shouldBeFalse()
          useSmartBar.shouldBeFalse()
  
          amountModel.secondaryAmount.shouldBe("0 sats")
          amountContextLineTreatment.shouldBe(LabelTreatment.Secondary)
          onSwapCurrencyClick.shouldNotBeNull()
          shouldTriggerContextualErrorFeedback.shouldBeFalse()
          primaryButton.isEnabled.shouldBeFalse()
        }

        val primaryAmountBelowBalance = balancePrimaryAmount - FiatMoney.usd(0.1)
        val secondaryAmountBelowBalance = BitcoinMoney(
          currency = BTC,
          primaryAmountBelowBalance.value.divide(
            conversionRate.toBigDecimal(),
            BTC.decimalMode()
          )
        )
        moneyCalculatorUiStateMachine.emitModel(
          defaultMoneyCalculatorModel.copy(
            primaryAmount = primaryAmountBelowBalance,
            secondaryAmount = secondaryAmountBelowBalance
          )
        )

        // With input; we should keep the amount enabled and the continue button disabled.
        awaitBody<TransferAmountBodyModel> {
          amountDisabled.shouldBeFalse()
          useSmartBar.shouldBeFalse()
          amountModel.secondaryAmount.shouldBe("Amount exceeds available balance")
          amountContextLineTreatment.shouldBe(LabelTreatment.Destructive)
          onSwapCurrencyClick.shouldBeNull()
          shouldTriggerContextualErrorFeedback.shouldBeTrue()
          primaryButton.isEnabled.shouldBeFalse()
        }
      }
    }
  }

  test("sell flow shows minimum sell message at zero balance then exceeds-balance on entry") {
    bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
      balance = BitcoinBalanceFake(confirmed = BitcoinMoney.zero())
    )

    val zeroAmountModel =
      defaultMoneyCalculatorModel.copy(
        primaryAmount = FiatMoney.zeroUsd(),
        secondaryAmount = BitcoinMoney.zero(),
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$0",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "0 sats"
          )
      )
    moneyCalculatorUiStateMachine.emitModel(zeroAmountModel)

    stateMachine.test(sellProps.copy(initialAmount = FiatMoney.zeroUsd())) {
      awaitBody<TransferAmountBodyModel> {
        amountDisabled.shouldBeFalse()

        amountModel.secondaryAmount.shouldBe("0 sats")
        amountContextLineTreatment.shouldBe(LabelTreatment.Secondary)
        onSwapCurrencyClick.shouldNotBeNull()
        shouldTriggerContextualErrorFeedback.shouldBeFalse()
        primaryButton.shouldBeDisabled()
      }

      moneyCalculatorUiStateMachine.emitModel(
        defaultMoneyCalculatorModel.copy(
          primaryAmount = FiatMoney.usd(1.0),
          secondaryAmount = BitcoinMoney.btc(0.3),
          amountModel =
            MoneyAmountEntryModel(
              primaryAmount = "$1.00",
              primaryAmountGhostedSubstringRange = null,
              secondaryAmount = "30,000,000 sats"
            )
        )
      )

      awaitBody<TransferAmountBodyModel> {
        amountDisabled.shouldBeFalse()

        amountModel.secondaryAmount.shouldBe("Amount exceeds available balance")
        amountContextLineTreatment.shouldBe(LabelTreatment.Destructive)
        onSwapCurrencyClick.shouldBeNull()
        shouldTriggerContextualErrorFeedback.shouldBeTrue()
        primaryButton.shouldBeDisabled()
      }
    }
  }

  test("Should keep above-balance state when send all is unavailable and amount also exceeds max") {
    val primaryAmountAboveBalance = balancePrimaryAmount + FiatMoney.usd(0.1)
    val secondaryAmountAboveBalance = BitcoinMoney(
      currency = BTC,
      primaryAmountAboveBalance.value.divide(
        conversionRate.toBigDecimal(),
        BTC.decimalMode()
      )
    )
    moneyCalculatorUiStateMachine.emitModel(
      defaultMoneyCalculatorModel.copy(
        primaryAmount = primaryAmountAboveBalance,
        secondaryAmount = secondaryAmountAboveBalance
      )
    )

    stateMachine.test(
      props.copy(
        flow =
          TransferAmountEntryUiProps.Flow.Send(
            allowSendAll = false,
            maxAmount = BitcoinMoney.btc(1.0)
          )
      )
    ) {
      awaitBody<TransferAmountBodyModel> {
        amountDisabled.shouldBeFalse()

        amountModel.secondaryAmount.shouldBe("Amount exceeds available balance")
        amountContextLineTreatment.shouldBe(LabelTreatment.Destructive)
        onSwapCurrencyClick.shouldBeNull()
        shouldTriggerContextualErrorFeedback.shouldBeTrue()
        primaryButton.shouldBeDisabled()
        cardModel.shouldBeNull()
      }

      transferCardUiStateMachine.props.transferAmountState
        .shouldBe(TransferAmountUiState.InvalidAmountEnteredUiState.InvalidAmountEqualOrAboveBalanceUiState)
    }
  }

  test("legacy hardware unavailable banner callback shows explanatory bottom sheet") {
    appFunctionalityService.status.value = LimitedFunctionality(F8eUnreachable(lastReachableTime = null))

    stateMachine.test(props) {
      awaitBody<TransferAmountBodyModel>()

      transferCardUiStateMachine.props.onHardwareRequiredClick()

      val hardwareRequiredSheet =
        awaitItem().bottomSheetModel.shouldNotBeNull().body.shouldBeInstanceOf<FormBodyModel>()
      hardwareRequiredSheet.header.shouldNotBeNull().headline.shouldBe("Bitkey Services Unavailable")
      hardwareRequiredSheet.header.shouldNotBeNull().sublineModel.shouldNotBeNull().string.shouldBe(
        "Fiat exchange rates are unavailable and your Bitkey device is required for all transactions."
      )
      hardwareRequiredSheet.primaryButton.shouldHaveText("Got it")
      hardwareRequiredSheet.primaryButton.click()

      awaitItem().bottomSheetModel.shouldBeNull()
    }
  }

  test("given exchange rates are null, should not show fiat amount") {
    stateMachine.test(props.copy(exchangeRates = null, initialAmount = BitcoinMoney.btc(1.0))) {
      awaitBody<TransferAmountBodyModel> {
        toolbar.middleAccessory.shouldNotBeNull().subtitle.shouldBe("500,000,000 sats available")
      }
    }
  }

  test("Entering amount should change requiresHardware status") {
    // Emit a zero entry
    moneyCalculatorUiStateMachine.emitModel(
      MoneyCalculatorModel(
        primaryAmount = FiatMoney.zeroUsd(),
        secondaryAmount = BitcoinMoney.zero(),
        amountModel = MoneyAmountEntryModel(
          primaryAmount = "$0",
          primaryAmountGhostedSubstringRange = null,
          secondaryAmount = "0 sats"
        ),
        keypadModel = KeypadModel(showDecimal = true, onButtonPress = {})
      )
    )

    stateMachine.test(props) {
      // Amount entered should be zero right now, so requiresHardware should be false
      awaitBody<TransferAmountBodyModel>()

      moneyCalculatorUiStateMachine.emitModel(defaultMoneyCalculatorModel)

      awaitBody<TransferAmountBodyModel>()
    }
  }

  test("Entering amount above balance should use balance to check if hardware needed") {
    val amountAboveBalance = defaultMoneyCalculatorModel.copy(
      primaryAmount = FiatMoney.usd(21.0),
      secondaryAmount = BitcoinMoney.btc(10.0),
      amountModel =
        MoneyAmountEntryModel(
          primaryAmount = "$21.00",
          primaryAmountGhostedSubstringRange = null,
          secondaryAmount = "1,000,000,000 sats"
        )
    )

    moneyCalculatorUiStateMachine.emitModel(amountAboveBalance)

    stateMachine.test(props) {
      awaitBody<TransferAmountBodyModel>()
    }
  }

  // TODO(W-1789): fix and enable test - it currently fails when targeting iOS.
  xtest("currency values swap in moneyCalculatorStateMachine onSwapCurrencyClick") {
    stateMachine.test(props) {
      awaitBody<TransferAmountBodyModel> {
        moneyCalculatorUiStateMachine.props.inputAmountCurrency.shouldBe(USD)
        moneyCalculatorUiStateMachine.props.secondaryDisplayAmountCurrency
          .shouldBe(BTC)
      }
      awaitBody<TransferAmountBodyModel> {
        moneyCalculatorUiStateMachine.props.inputAmountCurrency.shouldBe(USD)
        moneyCalculatorUiStateMachine.props.secondaryDisplayAmountCurrency
          .shouldBe(BTC)
        onSwapCurrencyClick?.invoke()
      }

      // TODO(W-1789): fix test - this model currently does not emit.
      awaitBody<TransferAmountBodyModel> {
        moneyCalculatorUiStateMachine.props.inputAmountCurrency.shouldBe(BTC)
        moneyCalculatorUiStateMachine.props.secondaryDisplayAmountCurrency
          .shouldBe(USD)
      }

      awaitBody<TransferAmountBodyModel> {
        moneyCalculatorUiStateMachine.props.inputAmountCurrency.shouldBe(BTC)
        moneyCalculatorUiStateMachine.props.secondaryDisplayAmountCurrency
          .shouldBe(USD)
      }
    }
  }
})
