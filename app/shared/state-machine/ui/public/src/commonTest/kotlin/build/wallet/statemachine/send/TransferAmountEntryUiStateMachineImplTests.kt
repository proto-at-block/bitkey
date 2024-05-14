package build.wallet.statemachine.send

import app.cash.turbine.plusAssign
import build.wallet.availability.NetworkReachability
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.SendAll
import build.wallet.bitkey.factor.SigningFactor
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.coroutines.turbine.turbines
import build.wallet.limit.MobilePaySpendingPolicyMock
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import build.wallet.money.currency.USD
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.money.calculator.MoneyCalculatorModel
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiProps
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiStateMachine
import build.wallet.statemachine.ui.matchers.shouldBeDisabled
import build.wallet.statemachine.ui.matchers.shouldHaveTitle
import build.wallet.statemachine.ui.robots.click
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

  val spendingPolicy = MobilePaySpendingPolicyMock(turbines::create)
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val stateMachine = TransferAmountEntryUiStateMachineImpl(
    currencyConverter = CurrencyConverterFake(conversionRate = 3.3333),
    moneyCalculatorUiStateMachine = moneyCalculatorUiStateMachine,
    mobilePaySpendingPolicy = spendingPolicy,
    moneyDisplayFormatter = MoneyDisplayFormatterFake,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository
  )

  fun ActiveKeyboxLoadedFake(balance: BitcoinBalance): ActiveFullAccountLoadedData {
    return ActiveKeyboxLoadedDataMock.copy(
      transactionsData = ActiveKeyboxLoadedDataMock.transactionsData.copy(balance = balance)
    )
  }

  val onContinueClickCalls = turbines.create<ContinueTransferParams>("onContinueClick calls")

  val props =
    TransferAmountEntryUiProps(
      onBack = {},
      accountData =
        ActiveKeyboxLoadedFake(
          balance = BitcoinBalanceFake(confirmed = bitcoinBalance)
        ),
      initialAmount = FiatMoney.usd(1.0),
      onContinueClick = { onContinueClickCalls += it },
      exchangeRates = emptyImmutableList(),
      f8eReachability = NetworkReachability.REACHABLE
    )

  afterTest {
    moneyCalculatorUiStateMachine.emitModel(defaultMoneyCalculatorModel)
  }

  test("initial balance amount and balance update") {
    stateMachine.test(props) {
      awaitScreenWithBody<TransferAmountBodyModel> {
        toolbar.middleAccessory.shouldNotBeNull().subtitle.shouldBe("\$16.67 available")
        moneyCalculatorUiStateMachine.props.inputAmountCurrency.shouldBe(USD)
        moneyCalculatorUiStateMachine.props.secondaryDisplayAmountCurrency
          .shouldBe(BTC)
      }

      updateProps(
        props.copy(
          accountData =
            ActiveKeyboxLoadedFake(
              balance = BitcoinBalanceFake(confirmed = BitcoinMoney.btc(10.0))
            )
        )
      )

      spendingPolicy.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(defaultSecondaryAmount)
      // After balance update – now with amount from balance provider
      awaitScreenWithBody<TransferAmountBodyModel> {
        toolbar.middleAccessory.shouldNotBeNull().subtitle.shouldBe("\$33.33 available")
      }
    }
  }

  test("initial amount in btc") {
    stateMachine.test(props.copy(initialAmount = BitcoinMoney.btc(1.0))) {
      spendingPolicy.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(defaultSecondaryAmount)
      awaitScreenWithBody<TransferAmountBodyModel> {
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
      spendingPolicy.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(defaultSecondaryAmount)
      awaitScreenWithBody<TransferAmountBodyModel> {
        toolbar.middleAccessory.shouldNotBeNull().subtitle.shouldBe("\$16.67 available")
      }

      moneyCalculatorUiStateMachine.emitModel(
        defaultMoneyCalculatorModel.copy(
          primaryAmount = balancePrimaryAmount,
          secondaryAmount = balanceSecondaryAmount
        )
      )

      spendingPolicy.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(balanceSecondaryAmount)
      awaitScreenWithBody<TransferAmountBodyModel> {
        cardModel.shouldNotBeNull()
          .title
          .string
          .shouldBe("Send Max (balance minus fees)")
      }
    }
  }

  test("entered amount above balance in fiat") {
    stateMachine.test(props) {
      spendingPolicy.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(defaultSecondaryAmount)
      awaitScreenWithBody<TransferAmountBodyModel> {
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

      awaitScreenWithBody<TransferAmountBodyModel> {
        spendingPolicy.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(balanceSecondaryAmount)

        // We should show smart bar
        cardModel.shouldNotBeNull()
          .title
          .string
          .shouldBe("Send Max (balance minus fees)")
      }
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

      stateMachine.test(props) {
        spendingPolicy.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(balanceSecondaryAmount)
        awaitScreenWithBody<TransferAmountBodyModel> {
          amountDisabled.shouldBeTrue()
          primaryButton.shouldBeDisabled()
          cardModel
            .shouldNotBeNull()
            .shouldHaveTitle("Send Max (balance minus fees)")
            .click()
        }

        onContinueClickCalls.awaitItem().shouldBe(
          ContinueTransferParams(
            sendAmount = SendAll,
            fiatMoney = FiatMoney.usd(16.67),
            requiredSigner = SigningFactor.Hardware,
            spendingLimit = null
          )
        )
      }
    }

    test("Should show approval required") {
      stateMachine.test(props) {
        spendingPolicy.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(defaultSecondaryAmount)
        awaitScreenWithBody<TransferAmountBodyModel> {
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

      stateMachine.test(props) {
        spendingPolicy.getDailySpendingLimitStatusCalls.awaitItem()
          .shouldBe(secondaryAmountBelowBalance)
        awaitScreenWithBody<TransferAmountBodyModel> {
          cardModel
            .shouldNotBeNull()
            .shouldHaveTitle("Bitkey approval required")
          amountDisabled.shouldBeFalse()
        }
      }
    }

    test("Should not show smart bar when user has no balance") {
      val zeroBalanceProps =
        props.copy(
          accountData =
            ActiveKeyboxLoadedFake(
              balance = BitcoinBalanceFake(confirmed = BitcoinMoney.btc(0.0))
            ),
          initialAmount = FiatMoney.usd(0.0)
        )

      stateMachine.test(zeroBalanceProps) {
        spendingPolicy.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(defaultSecondaryAmount)

        // Set calculator to simulate returning zero as input
        moneyCalculatorUiStateMachine.emitModel(
          defaultMoneyCalculatorModel.copy(
            primaryAmount = FiatMoney.zero(USD),
            secondaryAmount = BitcoinMoney.zero()
          )
        )

        spendingPolicy.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(BitcoinMoney.zero())
        awaitScreenWithBody<TransferAmountBodyModel> {
          amountDisabled.shouldBeTrue()
          primaryButton.isEnabled.shouldBeFalse()
          cardModel.shouldBeNull()
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

        spendingPolicy.getDailySpendingLimitStatusCalls.awaitItem()
          .shouldBe(secondaryAmountBelowBalance)
        // With input; we should show a disabled hero amount, enabled continue button, and no smart bar.
        awaitScreenWithBody<TransferAmountBodyModel> {
          amountDisabled.shouldBeFalse()
          primaryButton.isEnabled.shouldBeFalse()
          cardModel.shouldBeNull()
        }
        // Emitted because requiresHardware changes to true, but we do not show banner because we have no balance.
        awaitScreenWithBody<TransferAmountBodyModel> {
          cardModel.shouldBeNull()
        }
      }
    }
  }

  test("given exchange rates are null, should not show fiat amount") {
    stateMachine.test(props.copy(exchangeRates = null, initialAmount = BitcoinMoney.btc(1.0))) {
      spendingPolicy.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(defaultSecondaryAmount)
      awaitScreenWithBody<TransferAmountBodyModel> {
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
      spendingPolicy.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(BitcoinMoney.zero())
      // Amount entered should be zero right now, so requiresHardware should be false
      awaitScreenWithBody<TransferAmountBodyModel> {
        cardModel.shouldBeNull()
      }

      moneyCalculatorUiStateMachine.emitModel(defaultMoneyCalculatorModel)
      spendingPolicy.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(defaultSecondaryAmount)

      awaitScreenWithBody<TransferAmountBodyModel> {
        cardModel?.shouldHaveTitle("Bitkey approval required")
      }
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
      spendingPolicy.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(balanceSecondaryAmount)

      awaitScreenWithBody<TransferAmountBodyModel> {
        cardModel?.shouldHaveTitle("Send Max (balance minus fees)")
      }
    }
  }

  // TODO(W-1789): fix and enable test - it currently fails when targeting iOS.
  xtest("currency values swap in moneyCalculatorStateMachine onSwapCurrencyClick") {
    stateMachine.test(props) {
      awaitScreenWithBody<TransferAmountBodyModel> {
        moneyCalculatorUiStateMachine.props.inputAmountCurrency.shouldBe(USD)
        moneyCalculatorUiStateMachine.props.secondaryDisplayAmountCurrency
          .shouldBe(BTC)
      }
      awaitScreenWithBody<TransferAmountBodyModel> {
        moneyCalculatorUiStateMachine.props.inputAmountCurrency.shouldBe(USD)
        moneyCalculatorUiStateMachine.props.secondaryDisplayAmountCurrency
          .shouldBe(BTC)
        onSwapCurrencyClick()
      }

      // TODO(W-1789): fix test - this model currently does not emit.
      awaitScreenWithBody<TransferAmountBodyModel> {
        moneyCalculatorUiStateMachine.props.inputAmountCurrency.shouldBe(BTC)
        moneyCalculatorUiStateMachine.props.secondaryDisplayAmountCurrency
          .shouldBe(USD)
      }

      awaitScreenWithBody<TransferAmountBodyModel> {
        moneyCalculatorUiStateMachine.props.inputAmountCurrency.shouldBe(BTC)
        moneyCalculatorUiStateMachine.props.secondaryDisplayAmountCurrency
          .shouldBe(USD)
      }
    }
  }
})
