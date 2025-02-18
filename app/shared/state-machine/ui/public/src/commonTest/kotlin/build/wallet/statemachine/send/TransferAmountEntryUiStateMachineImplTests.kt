package build.wallet.statemachine.send

import app.cash.turbine.plusAssign
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.TransactionsDataMock
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import build.wallet.money.currency.USD
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.StateMachineMock
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
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

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

  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val bitcoinWalletService = BitcoinWalletServiceFake()
  val stateMachine = TransferAmountEntryUiStateMachineImpl(
    currencyConverter = CurrencyConverterFake(conversionRate = 3.3333),
    moneyCalculatorUiStateMachine = moneyCalculatorUiStateMachine,
    moneyDisplayFormatter = MoneyDisplayFormatterFake,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
    bitcoinWalletService = bitcoinWalletService,
    transferCardUiStateMachine = transferCardUiStateMachine,
    appFunctionalityService = AppFunctionalityServiceFake()
  )

  val onContinueClickCalls = turbines.create<ContinueTransferParams>("onContinueClick calls")

  val props =
    TransferAmountEntryUiProps(
      onBack = {},
      initialAmount = FiatMoney.usd(1.0),
      onContinueClick = { onContinueClickCalls += it },
      exchangeRates = emptyImmutableList(),
      allowSendAll = true
    )

  beforeTest {
    bitcoinWalletService.reset()

    bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
      balance = BitcoinBalanceFake(confirmed = bitcoinBalance)
    )
  }

  afterTest {
    moneyCalculatorUiStateMachine.emitModel(defaultMoneyCalculatorModel)
  }

  test("initial balance amount and balance update") {
    stateMachine.testWithVirtualTime(props) {
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
    stateMachine.testWithVirtualTime(props.copy(initialAmount = BitcoinMoney.btc(1.0))) {
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
    stateMachine.testWithVirtualTime(props) {
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
    stateMachine.testWithVirtualTime(props) {
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

  context("Send Max is Available") {
    test("Should show smart bar and disable amount hero if spending above balance") {
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

      stateMachine.testWithVirtualTime(props) {
        awaitBody<TransferAmountBodyModel> {
          amountDisabled.shouldBeTrue()
          primaryButton.shouldBeDisabled()
        }
      }
    }

    test("Should show approval required") {
      stateMachine.testWithVirtualTime(props) {
        awaitBody<TransferAmountBodyModel> {
          toolbar.middleAccessory.shouldNotBeNull().subtitle.shouldBe("\$16.67 available")
        }
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

      stateMachine.testWithVirtualTime(props) {
        awaitBody<TransferAmountBodyModel> {
          amountDisabled.shouldBeFalse()
        }
      }
    }

    test("Should not show smart bar when user has no balance") {
      bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
        balance = BitcoinBalanceFake(confirmed = BitcoinMoney.btc(0.0))
      )

      val zeroBalanceProps = props.copy(initialAmount = FiatMoney.usd(0.0))

      stateMachine.testWithVirtualTime(zeroBalanceProps) {
        // Set calculator to simulate returning zero as input
        moneyCalculatorUiStateMachine.emitModel(
          defaultMoneyCalculatorModel.copy(
            primaryAmount = FiatMoney.zero(USD),
            secondaryAmount = BitcoinMoney.zero()
          )
        )

        awaitBody<TransferAmountBodyModel> {
          amountDisabled.shouldBeTrue()
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

        // With input; we should show a disabled hero amount, enabled continue button, and no smart bar.
        awaitBody<TransferAmountBodyModel> {
          amountDisabled.shouldBeFalse()
          primaryButton.isEnabled.shouldBeFalse()
        }
        // Emitted because requiresHardware changes to true, but we do not show banner because we have no balance.
        awaitBody<TransferAmountBodyModel>()
      }
    }
  }

  test("Should not show smart bar and disable amount hero if spending above balance") {
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

    stateMachine.testWithVirtualTime(props.copy(allowSendAll = false)) {
      awaitBody<TransferAmountBodyModel> {
        amountDisabled.shouldBeTrue()
        primaryButton.shouldBeDisabled()
        cardModel.shouldBeNull()
      }
    }
  }

  test("given exchange rates are null, should not show fiat amount") {
    stateMachine.testWithVirtualTime(props.copy(exchangeRates = null, initialAmount = BitcoinMoney.btc(1.0))) {
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

    stateMachine.testWithVirtualTime(props) {
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

    stateMachine.testWithVirtualTime(props) {
      awaitBody<TransferAmountBodyModel>()
    }
  }

  // TODO(W-1789): fix and enable test - it currently fails when targeting iOS.
  xtest("currency values swap in moneyCalculatorStateMachine onSwapCurrencyClick") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<TransferAmountBodyModel> {
        moneyCalculatorUiStateMachine.props.inputAmountCurrency.shouldBe(USD)
        moneyCalculatorUiStateMachine.props.secondaryDisplayAmountCurrency
          .shouldBe(BTC)
      }
      awaitBody<TransferAmountBodyModel> {
        moneyCalculatorUiStateMachine.props.inputAmountCurrency.shouldBe(USD)
        moneyCalculatorUiStateMachine.props.secondaryDisplayAmountCurrency
          .shouldBe(BTC)
        onSwapCurrencyClick()
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
