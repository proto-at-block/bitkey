package build.wallet.statemachine.send

import androidx.compose.runtime.*
import build.wallet.bitcoin.balance.BitcoinBalance.Companion.ZeroBalance
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.SendAll
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.coroutines.scopes.mapAsStateFlow
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.Money
import build.wallet.money.currency.BTC
import build.wallet.money.currency.Currency
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.exchange.ExchangeRate
import build.wallet.money.exchange.ExchangeRateService
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.*
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiProps
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiStateMachine
import build.wallet.statemachine.send.amountentry.SmartBarUiProps
import build.wallet.statemachine.send.amountentry.SmartBarUiStateMachine
import build.wallet.ui.components.label.LabelTreatment.Destructive
import build.wallet.ui.components.label.LabelTreatment.Secondary
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableList

@BitkeyInject(ActivityScope::class)
class TransferAmountEntryUiStateMachineImpl(
  private val currencyConverter: CurrencyConverter,
  private val moneyCalculatorUiStateMachine: MoneyCalculatorUiStateMachine,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val bitcoinWalletService: BitcoinWalletService,
  private val smartBarUiStateMachine: SmartBarUiStateMachine,
  private val exchangeRateService: ExchangeRateService,
) : TransferAmountEntryUiStateMachine {
  // TODO(W-703): derive from BDK
  private val dustLimit = BitcoinMoney.sats(546)

  @Composable
  @Suppress("CyclomaticComplexMethod")
  override fun model(props: TransferAmountEntryUiProps): ScreenModel {
    val flow = props.flow
    val allowSendAll = (flow as? TransferAmountEntryUiProps.Flow.Send)?.allowSendAll ?: false
    val minimumAmount =
      when (flow) {
        is TransferAmountEntryUiProps.Flow.Send -> flow.minAmount
        is TransferAmountEntryUiProps.Flow.Sell -> flow.minAmount
      }
    val maximumAmount =
      when (flow) {
        is TransferAmountEntryUiProps.Flow.Send -> flow.maxAmount
        is TransferAmountEntryUiProps.Flow.Sell -> flow.maxAmount
      }
    val isSellFlow = flow is TransferAmountEntryUiProps.Flow.Sell
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

    val bitcoinBalance by remember {
      bitcoinWalletService.transactionsData()
        .mapAsStateFlow(scope) { it?.balance ?: ZeroBalance }
    }.collectAsState()

    val exchangeRateRefresh = rememberExchangeRateRefresh(props.exchangeRates)
    val exchangeRatesForCalculator = exchangeRateRefresh.exchangeRatesForCalculator

    // We convert the bitcoin balance to fiat if we have exchange rates, we don't grab the fiat balance
    // from the transactions data because we want to use the same exchange rates for the entire send flow.
    val fiatBalance: FiatMoney? = remember(exchangeRatesForCalculator, bitcoinBalance) {
      exchangeRatesForCalculator?.let {
        currencyConverter.convert(
          bitcoinBalance.total,
          fiatCurrency,
          it
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
        exchangeRates = exchangeRatesForCalculator
      )
    )

    val enteredBitcoinMoney = remember(calculatorModel) {
      calculatorModel.bitcoinMoney()
    }
    if (enteredBitcoinMoney == null) {
      return exchangeRateRefresh.model(
        onBack = props.onBack
      )
    }

    val enteredFiatMoney: FiatMoney? = remember(exchangeRatesForCalculator, calculatorModel) {
      // We don't have exchange rates, so we can't convert.
      exchangeRatesForCalculator?.let {
        calculatorModel.primaryAmount as? FiatMoney ?: calculatorModel.secondaryAmount as? FiatMoney
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
          // Amount entered is above balance and send all is allowed
          enteredAmountAboveBalance && allowSendAll -> TransferAmountUiState.ValidAmountEnteredUiState.AmountEqualOrAboveBalanceUiState
          // Amount entered is above balance and send all is *not* allowed
          enteredAmountAboveBalance && !allowSendAll -> TransferAmountUiState.InvalidAmountEnteredUiState.InvalidAmountEqualOrAboveBalanceUiState
          // Amount entered is less than minAmount
          minimumAmount != null && enteredBitcoinMoney < minimumAmount -> TransferAmountUiState.InvalidAmountEnteredUiState.AmountBelowMinimumUiState
          // Amount entered is greater than maxAmount
          maximumAmount != null && enteredBitcoinMoney > maximumAmount -> TransferAmountUiState.InvalidAmountEnteredUiState.AmountAboveMaximumUiState
          // Amount entered is below dust limit
          enteredBitcoinMoney < dustLimit -> TransferAmountUiState.InvalidAmountEnteredUiState.AmountBelowDustLimitUiState

          // Transfer amount is within bounds of balance
          else -> TransferAmountUiState.ValidAmountEnteredUiState.AmountBelowBalanceUiState
        }
      }
    }
    val hasEnteredNonZeroAmount by remember(enteredBitcoinMoney, enteredFiatMoney) {
      derivedStateOf {
        !enteredBitcoinMoney.isZero || enteredFiatMoney?.isZero == false
      }
    }
    val isAmountExceedsAvailableBalanceState by remember(
      transferAmountState,
      bitcoinBalance,
      hasEnteredNonZeroAmount
    ) {
      derivedStateOf {
        (bitcoinBalance.total.isZero && hasEnteredNonZeroAmount) ||
          transferAmountState is TransferAmountUiState.ValidAmountEnteredUiState.AmountEqualOrAboveBalanceUiState ||
          transferAmountState is TransferAmountUiState.InvalidAmountEnteredUiState.InvalidAmountEqualOrAboveBalanceUiState
      }
    }
    val minimumAmountDisplay by remember(
      minimumAmount,
      exchangeRatesForCalculator,
      currencyState.inputAmountCurrency
    ) {
      derivedStateOf {
        minimumAmount?.let { lowerBound ->
          when (currencyState.inputAmountCurrency) {
            BTC -> lowerBound
            else -> exchangeRatesForCalculator?.let {
              currencyConverter.convert(
                lowerBound,
                currencyState.inputAmountCurrency,
                it
              )?.rounded()
            } ?: lowerBound
          }
        }
      }
    }
    val maximumAmountDisplay by remember(
      maximumAmount,
      exchangeRatesForCalculator,
      currencyState.inputAmountCurrency
    ) {
      derivedStateOf {
        maximumAmount?.let { upperBound ->
          when (currencyState.inputAmountCurrency) {
            BTC -> upperBound
            else -> exchangeRatesForCalculator?.let {
              currencyConverter.convert(
                upperBound,
                currencyState.inputAmountCurrency,
                it
              )?.rounded()
            } ?: upperBound
          }
        }
      }
    }
    val sellContextLineOverride by remember(
      isSellFlow,
      isAmountExceedsAvailableBalanceState,
      hasEnteredNonZeroAmount,
      transferAmountState,
      minimumAmountDisplay,
      maximumAmountDisplay
    ) {
      derivedStateOf {
        if (!isSellFlow) return@derivedStateOf null
        when {
          isAmountExceedsAvailableBalanceState ->
            "Amount exceeds available balance"
          transferAmountState is TransferAmountUiState.InvalidAmountEnteredUiState.AmountAboveMaximumUiState ->
            maximumAmountDisplay?.let { "Maximum sell amount is ${moneyDisplayFormatter.format(it)}" }
          hasEnteredNonZeroAmount &&
            transferAmountState is TransferAmountUiState.InvalidAmountEnteredUiState.AmountBelowMinimumUiState ->
            minimumAmountDisplay?.let { "Minimum sell amount is ${moneyDisplayFormatter.format(it)}" }
          else -> null
        }
      }
    }
    val amountContextLineTreatment by remember(
      sellContextLineOverride,
      isAmountExceedsAvailableBalanceState
    ) {
      derivedStateOf {
        when {
          sellContextLineOverride != null -> Destructive
          isAmountExceedsAvailableBalanceState -> Destructive
          else -> Secondary
        }
      }
    }
    val shouldTriggerContextualErrorFeedback by remember(
      isSellFlow,
      isAmountExceedsAvailableBalanceState,
      transferAmountState
    ) {
      derivedStateOf {
        when {
          isSellFlow ->
            isAmountExceedsAvailableBalanceState ||
              transferAmountState is TransferAmountUiState.InvalidAmountEnteredUiState.AmountAboveMaximumUiState
          else -> isAmountExceedsAvailableBalanceState
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
          minimumAmount != null && enteredBitcoinMoney < minimumAmount -> true
          maximumAmount != null && enteredBitcoinMoney > maximumAmount -> true
          else -> false
        }
      }
    }
    val showSwapCurrencyControl by remember(
      isAmountExceedsAvailableBalanceState,
      calculatorModel.secondaryAmount,
      sellContextLineOverride
    ) {
      derivedStateOf {
        calculatorModel.secondaryAmount != null &&
          sellContextLineOverride == null &&
          !isAmountExceedsAvailableBalanceState
      }
    }

    val smartBarModel =
      if (isSellFlow) {
        null
      } else {
        smartBarUiStateMachine.model(
          props = SmartBarUiProps(
            transferAmountState = transferAmountState,
            onSendMaxClick = {
              props.onContinueClick(
                ContinueTransferParams(
                  SendAll
                )
              )
            }
          )
        )
      }

    val useSmartBar by remember(isSellFlow, allowSendAll, bitcoinBalance) {
      derivedStateOf { !isSellFlow && allowSendAll && !bitcoinBalance.total.isZero }
    }

    val bodyModel = TransferAmountBodyModel(
      onBack = props.onBack,
      balanceTitle = "$balancedFormatted available",
      amountModel =
        calculatorModel.amountModel.copy(
          secondaryAmount = sellContextLineOverride
            ?: if (isAmountExceedsAvailableBalanceState) {
              "Amount exceeds available balance"
            } else {
              calculatorModel.amountModel.secondaryAmount
            }
        ),
      keypadModel = calculatorModel.keypadModel,
      smartBarModel = smartBarModel,
      continueButtonEnabled = transferAmountState is TransferAmountUiState.ValidAmountEnteredUiState.AmountBelowBalanceUiState,
      amountDisabled =
        when {
          isSellFlow -> false
          enteredAmountAboveBalance -> false
          else -> disableTransferAmount
        },
      amountContextLineTreatment = amountContextLineTreatment,
      shouldTriggerContextualErrorFeedback = shouldTriggerContextualErrorFeedback,
      useSmartBar = useSmartBar,
      onContinueClick = {
        if (transferAmountState is TransferAmountUiState.ValidAmountEnteredUiState.AmountBelowBalanceUiState) {
          props.onContinueClick(
            ContinueTransferParams(
              ExactAmount(enteredBitcoinMoney)
            )
          )
        }
      },
      onSwapCurrencyClick =
        if (showSwapCurrencyControl) {
          {
            calculatorModel.secondaryAmount?.let { amountInSecondaryCurrency ->
              currencyState = currencyState.swapCurrency(
                amountInSecondaryCurrency = amountInSecondaryCurrency
              )
            }
          }
        } else {
          null
        }
    )

    return ScreenModel(
      body = bodyModel,
      presentationStyle = ScreenPresentationStyle.ModalFullScreen
    )
  }

  @Composable
  private fun rememberExchangeRateRefresh(
    propsExchangeRates: ImmutableList<ExchangeRate>?,
  ): ExchangeRateRefresh {
    val providedExchangeRates = propsExchangeRates?.takeUnless { it.isEmpty() }
    var refreshAttempt by remember { mutableIntStateOf(0) }
    var state: ExchangeRateRefreshState by remember(providedExchangeRates) {
      mutableStateOf(
        if (providedExchangeRates == null) {
          ExchangeRateRefreshState.NeedsRefresh
        } else {
          ExchangeRateRefreshState.HasValidProvidedRates
        }
      )
    }

    LaunchedEffect(refreshAttempt, providedExchangeRates) {
      if (state == ExchangeRateRefreshState.NeedsRefresh && providedExchangeRates == null) {
        state = ExchangeRateRefreshState.Loading
        exchangeRateService.syncRates()
          .onSuccess { rates ->
            state = if (rates.isEmpty()) {
              ExchangeRateRefreshState.FailedToLoadRates
            } else {
              ExchangeRateRefreshState.WaitingForProvidedRates
            }
          }
          .onFailure { state = ExchangeRateRefreshState.FailedToLoadRates }
      }
    }

    return ExchangeRateRefresh(
      exchangeRatesForCalculator = providedExchangeRates,
      state = state,
      onRetry = {
        state = if (providedExchangeRates == null) {
          ExchangeRateRefreshState.NeedsRefresh
        } else {
          ExchangeRateRefreshState.HasValidProvidedRates
        }
        refreshAttempt += 1
      }
    )
  }

  @Composable
  private fun ExchangeRateRefresh.model(onBack: () -> Unit): ScreenModel {
    return when (state) {
      ExchangeRateRefreshState.HasValidProvidedRates ->
        exchangeRatesProvidedButConversionUnavailableModel(onBack = onBack)
      ExchangeRateRefreshState.FailedToLoadRates ->
        exchangeRatesFailedToLoadModel(onBack = onBack)
      ExchangeRateRefreshState.NeedsRefresh,
      ExchangeRateRefreshState.Loading,
      ExchangeRateRefreshState.WaitingForProvidedRates,
      -> LoadingBodyModel(
        id = null,
        title = "Loading exchange rates...",
        onBack = onBack,
        eventTrackerShouldTrack = false
      ).asModalFullScreen()
    }
  }

  private fun ExchangeRateRefresh.exchangeRatesFailedToLoadModel(
    onBack: () -> Unit,
  ): ScreenModel {
    return exchangeRatesUnavailableErrorModel(
      subline = "We couldn’t load current exchange rates. Please check your connection and try again.",
      primaryButton = ButtonDataModel(
        text = "Try again",
        onClick = onRetry
      ),
      secondaryButton = ButtonDataModel(
        text = "Go back",
        onClick = onBack
      ),
      actionDescription = "Loading exchange rates for transfer amount",
      cause = Error("Bitcoin conversion unavailable for transfer amount entry"),
      onBack = onBack
    )
  }

  private fun ExchangeRateRefresh.exchangeRatesProvidedButConversionUnavailableModel(
    onBack: () -> Unit,
  ): ScreenModel {
    return exchangeRatesUnavailableErrorModel(
      subline = "We couldn’t calculate a Bitcoin amount from the current exchange rates. Please go back and try again.",
      primaryButton = ButtonDataModel(
        text = "Go back",
        onClick = onBack
      ),
      secondaryButton = null,
      actionDescription = "Calculating Bitcoin amount for transfer amount",
      cause = Error("Bitcoin conversion unavailable despite provided exchange rates"),
      onBack = onBack
    )
  }

  private fun exchangeRatesUnavailableErrorModel(
    subline: String,
    primaryButton: ButtonDataModel,
    secondaryButton: ButtonDataModel?,
    actionDescription: String,
    cause: Error,
    onBack: () -> Unit,
  ): ScreenModel {
    return ErrorFormBodyModel(
      title = "Exchange rates unavailable",
      subline = subline,
      primaryButton = primaryButton,
      secondaryButton = secondaryButton,
      onBack = onBack,
      eventTrackerScreenId = null,
      eventTrackerShouldTrack = false,
      errorData = ErrorData(
        segment = SendAppSegment,
        actionDescription = actionDescription,
        cause = cause
      )
    ).asModalFullScreen()
  }

  private data class ExchangeRateRefresh(
    val exchangeRatesForCalculator: ImmutableList<ExchangeRate>?,
    val state: ExchangeRateRefreshState,
    val onRetry: () -> Unit,
  )

  private enum class ExchangeRateRefreshState {
    HasValidProvidedRates,
    NeedsRefresh,
    Loading,
    WaitingForProvidedRates,
    FailedToLoadRates,
  }

  private fun build.wallet.statemachine.money.calculator.MoneyCalculatorModel.bitcoinMoney(): BitcoinMoney? =
    primaryAmount as? BitcoinMoney ?: secondaryAmount as? BitcoinMoney

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
}
