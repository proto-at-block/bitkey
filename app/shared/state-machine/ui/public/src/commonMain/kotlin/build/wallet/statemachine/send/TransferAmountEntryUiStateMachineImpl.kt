package build.wallet.statemachine.send

import androidx.compose.runtime.*
import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.FunctionalityFeatureStates
import build.wallet.bitcoin.balance.BitcoinBalance.Companion.ZeroBalance
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.SendAll
import build.wallet.bitcoin.transactions.TransactionsData
import build.wallet.bitcoin.transactions.TransactionsService
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.coroutines.scopes.mapAsStateFlow
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.Money
import build.wallet.money.currency.BTC
import build.wallet.money.currency.Currency
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiProps
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiStateMachine
import build.wallet.statemachine.send.amountentry.TransferCardUiProps
import build.wallet.statemachine.send.amountentry.TransferCardUiStateMachine

class TransferAmountEntryUiStateMachineImpl(
  private val currencyConverter: CurrencyConverter,
  private val moneyCalculatorUiStateMachine: MoneyCalculatorUiStateMachine,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val transactionsService: TransactionsService,
  private val transferCardUiStateMachine: TransferCardUiStateMachine,
  private val appFunctionalityService: AppFunctionalityService,
) : TransferAmountEntryUiStateMachine {
  // TODO(W-703): derive from BDK
  private val dustLimit = BitcoinMoney.sats(546)

  @Composable
  @Suppress("CyclomaticComplexMethod")
  override fun model(props: TransferAmountEntryUiProps): ScreenModel {
    val scope = rememberStableCoroutineScope()

    val fiatCurrency by remember { fiatCurrencyPreferenceRepository.fiatCurrencyPreference }
      .collectAsState()

    // Always start with the currency of the given amount as the primary currency
    // and the given fiat or BTC as secondary, whichever the amount isn't
    var currencyState by remember {
      mutableStateOf(
        CurrencyState(
          inputAmountCurrency = props.initialAmount.currency,
          secondaryDisplayAmountCurrency = if (props.initialAmount is BitcoinMoney) fiatCurrency else BTC,
          initialAmountInInputCurrency = props.initialAmount
        )
      )
    }

    var sheetState by remember {
      mutableStateOf<SheetState>(SheetState.Hidden)
    }

    val mobilePayAvailability by remember {
      appFunctionalityService.status
        .mapAsStateFlow(scope) { it.featureStates.mobilePay }
    }.collectAsState()

    val bitcoinBalance by remember {
      transactionsService.transactionsData()
        .mapAsStateFlow(scope) {
          when (it) {
            TransactionsData.LoadingTransactionsData -> ZeroBalance
            is TransactionsData.TransactionsLoadedData -> it.balance
          }
        }
    }.collectAsState()

    // We convert the bitcoin balance to fiat if we have exchange rates, we don't grab the fiat balance
    // from the transactions data because we want to use the same exchange rates for the entire send flow.
    val fiatBalance: FiatMoney? = remember(props.exchangeRates, bitcoinBalance) {
      props.exchangeRates?.let {
        currencyConverter.convert(
          bitcoinBalance.total,
          fiatCurrency,
          props.exchangeRates
        )?.rounded() as? FiatMoney
      }
    }

    val balancedFormatted = remember(currencyState, bitcoinBalance, fiatBalance) {
      when (currencyState.inputAmountCurrency) {
        BTC -> moneyDisplayFormatter.format(bitcoinBalance.total)
        else -> fiatBalance?.let { moneyDisplayFormatter.format(it) }.orEmpty()
      }
    }

    val calculatorModel = moneyCalculatorUiStateMachine.model(
      props = MoneyCalculatorUiProps(
        inputAmountCurrency = currencyState.inputAmountCurrency,
        secondaryDisplayAmountCurrency = currencyState.secondaryDisplayAmountCurrency,
        initialAmountInInputCurrency = currencyState.initialAmountInInputCurrency,
        exchangeRates = props.exchangeRates
      )
    )

    val enteredBitcoinMoney: BitcoinMoney = remember(calculatorModel) {
      if (calculatorModel.primaryAmount is BitcoinMoney) {
        calculatorModel.primaryAmount
      } else if (calculatorModel.secondaryAmount is BitcoinMoney) {
        calculatorModel.secondaryAmount
      } else {
        error("Entered bitcoin money is neither primary or secondary. This should never happen.")
      }
    }

    val enteredFiatMoney: FiatMoney? = remember(props.exchangeRates, calculatorModel) {
      // We don't have exchange rates, so we can't convert.
      props.exchangeRates?.let {
        if (calculatorModel.primaryAmount is FiatMoney) {
          calculatorModel.primaryAmount
        } else if (calculatorModel.secondaryAmount is FiatMoney) {
          calculatorModel.secondaryAmount
        } else {
          // If neither primary or secondary is fiat, assume fiat is unavailable
          null
        }
      }
    }

    // Base the determination of the entered amount being above the balance
    // based on the currency it's entered in.
    val enteredAmountAboveBalance: Boolean by remember(
      currencyState,
      enteredBitcoinMoney,
      bitcoinBalance
    ) {
      derivedStateOf {
        when (fiatBalance) {
          null -> enteredBitcoinMoney >= bitcoinBalance.total
          else ->
            when (currencyState.inputAmountCurrency) {
              BTC -> enteredBitcoinMoney >= bitcoinBalance.total
              else ->
                enteredFiatMoney?.let {
                  enteredFiatMoney >= fiatBalance
                }
                  ?: error("Entered fiat money is null so it should not be primary currency. This should never happen.")
            }
        }
      }
    }

    val transferAmountState by remember(
      enteredBitcoinMoney,
      enteredAmountAboveBalance,
      bitcoinBalance
    ) {
      derivedStateOf {
        when {
          // Check for invalid cases first
          // User entered an amount while having a zero balance
          bitcoinBalance.total.isZero -> TransferAmountUiState.InvalidAmountEnteredUiState.AmountWithZeroBalanceUiState
          // Amount entered is less than minAmount
          props.minAmount != null && enteredBitcoinMoney < props.minAmount -> TransferAmountUiState.InvalidAmountEnteredUiState.AmountBelowMinimumUiState
          // Amount entered is greater than maxAmount
          props.maxAmount != null && enteredBitcoinMoney > props.maxAmount -> TransferAmountUiState.InvalidAmountEnteredUiState.AmountAboveMaximumUiState
          // Amount entered is above balance and send all is allowed
          enteredAmountAboveBalance && props.allowSendAll -> TransferAmountUiState.ValidAmountEnteredUiState.AmountEqualOrAboveBalanceUiState
          // Amount entered is above balance and send all is *not* allowed
          enteredAmountAboveBalance && !props.allowSendAll -> TransferAmountUiState.InvalidAmountEnteredUiState.InvalidAmountEqualOrAboveBalanceUiState
          // Amount entered is below dust limit
          enteredBitcoinMoney < dustLimit -> TransferAmountUiState.InvalidAmountEnteredUiState.AmountBelowDustLimitUiState

          // Transfer amount is within bounds of balance
          else -> TransferAmountUiState.ValidAmountEnteredUiState.AmountBelowBalanceUiState
        }
      }
    }

    val disableTransferAmount by remember(
      enteredBitcoinMoney,
      enteredAmountAboveBalance,
      bitcoinBalance
    ) {
      derivedStateOf {
        when {
          bitcoinBalance.total.isZero -> !(enteredBitcoinMoney.isZero || (enteredFiatMoney?.isZero == true))
          enteredAmountAboveBalance -> true
          props.minAmount != null && enteredBitcoinMoney < props.minAmount -> true
          props.maxAmount != null && enteredBitcoinMoney > props.maxAmount -> true
          else -> false
        }
      }
    }

    val cardModel = transferCardUiStateMachine.model(
      props = TransferCardUiProps(
        bitcoinBalance = bitcoinBalance,
        enteredBitcoinMoney = enteredBitcoinMoney,
        transferAmountState = transferAmountState,
        onSendMaxClick = {
          props.onContinueClick(
            ContinueTransferParams(
              SendAll
            )
          )
        },
        onHardwareRequiredClick = {
          sheetState = if (mobilePayAvailability == FunctionalityFeatureStates.FeatureState.Unavailable) {
            SheetState.HardwareRequiredSheetState
          } else {
            SheetState.Hidden
          }
        }
      )
    )

    val bodyModel = TransferAmountBodyModel(
      onBack = props.onBack,
      balanceTitle = "$balancedFormatted available",
      amountModel = calculatorModel.amountModel,
      keypadModel = calculatorModel.keypadModel,
      cardModel = cardModel,
      continueButtonEnabled = transferAmountState is TransferAmountUiState.ValidAmountEnteredUiState.AmountBelowBalanceUiState,
      amountDisabled = disableTransferAmount,
      onContinueClick = {
        if (transferAmountState is TransferAmountUiState.ValidAmountEnteredUiState) {
          props.onContinueClick(
            ContinueTransferParams(
              ExactAmount(enteredBitcoinMoney)
            )
          )
        }
      },
      onSwapCurrencyClick = {
        if (calculatorModel.secondaryAmount != null) {
          currencyState = currencyState.swapCurrency(
            amountInSecondaryCurrency = calculatorModel.secondaryAmount
          )
        }
      }
    )

    val bottomSheetModel = when (sheetState) {
      is SheetState.Hidden -> null
      is SheetState.HardwareRequiredSheetState ->
        SheetModel(
          onClosed = { sheetState = SheetState.Hidden },
          body = ErrorFormBodyModel(
            title = "Bitkey Services Unavailable",
            subline = "Fiat exchange rates are unavailable and your Bitkey device is required for all transactions.",
            primaryButton =
              ButtonDataModel(
                text = "Got it",
                onClick = { sheetState = SheetState.Hidden }
              ),
            renderContext = RenderContext.Sheet,
            eventTrackerScreenId = null
          )
        )
    }

    return ScreenModel(
      body = bodyModel,
      presentationStyle = ScreenPresentationStyle.ModalFullScreen,
      bottomSheetModel = bottomSheetModel
    )
  }

  private data class CurrencyState(
    val inputAmountCurrency: Currency,
    val secondaryDisplayAmountCurrency: Currency,
    val initialAmountInInputCurrency: Money,
  ) {
    fun swapCurrency(amountInSecondaryCurrency: Money): CurrencyState {
      return copy(
        inputAmountCurrency = secondaryDisplayAmountCurrency,
        secondaryDisplayAmountCurrency = inputAmountCurrency,
        initialAmountInInputCurrency = amountInSecondaryCurrency
      )
    }
  }

  private sealed interface SheetState {
    /**
     * Sheet is shown to the user to indicate that hardware is required for all transactions. Shown
     * when the user clicks on the "Hardware Required" banner and f8e is unavailable.
     */
    data object HardwareRequiredSheetState : SheetState

    /** No sheet is shown on the screen */
    data object Hidden : SheetState
  }
}
