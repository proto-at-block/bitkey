package build.wallet.statemachine.send

import androidx.compose.runtime.*
import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.FunctionalityFeatureStates
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
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiProps
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiStateMachine
import build.wallet.statemachine.send.amountentry.TransferCardUiProps
import build.wallet.statemachine.send.amountentry.TransferCardUiStateMachine
import build.wallet.ui.components.label.LabelTreatment.Destructive
import build.wallet.ui.components.label.LabelTreatment.Secondary

@BitkeyInject(ActivityScope::class)
class TransferAmountEntryUiStateMachineImpl(
  private val currencyConverter: CurrencyConverter,
  private val moneyCalculatorUiStateMachine: MoneyCalculatorUiStateMachine,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val bitcoinWalletService: BitcoinWalletService,
  private val transferCardUiStateMachine: TransferCardUiStateMachine,
  private val appFunctionalityService: AppFunctionalityService,
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
    val isDesignSystemV2Enabled = true
    val fiatCurrency by remember { fiatCurrencyPreferenceRepository.fiatCurrencyPreference }
      .collectAsState()

    var sheetState by remember {
      mutableStateOf<SheetState>(SheetState.Hidden)
    }

    val mobilePayAvailability by remember {
      appFunctionalityService.status
        .mapAsStateFlow(scope) { it.featureStates.mobilePay }
    }.collectAsState()

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
      props.exchangeRates,
      currencyState.inputAmountCurrency
    ) {
      derivedStateOf {
        minimumAmount?.let { lowerBound ->
          when (currencyState.inputAmountCurrency) {
            BTC -> lowerBound
            else -> props.exchangeRates?.let {
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
      props.exchangeRates,
      currencyState.inputAmountCurrency
    ) {
      derivedStateOf {
        maximumAmount?.let { upperBound ->
          when (currencyState.inputAmountCurrency) {
            BTC -> upperBound
            else -> props.exchangeRates?.let {
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
    val amountContextLineTreatment by remember(sellContextLineOverride, isAmountExceedsAvailableBalanceState) {
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
          isSellFlow -> isAmountExceedsAvailableBalanceState ||
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

    val cardModel =
      if (isSellFlow) {
        null
      } else {
        transferCardUiStateMachine.model(
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
              if (
                !isDesignSystemV2Enabled &&
                mobilePayAvailability == FunctionalityFeatureStates.FeatureState.Unavailable
              ) {
                sheetState = SheetState.HardwareRequiredSheetState
              }
            }
          )
        )
      }

    val useSmartBar by remember(isDesignSystemV2Enabled, isSellFlow, transferAmountState) {
      derivedStateOf {
        isDesignSystemV2Enabled &&
          !isSellFlow &&
          transferAmountState is TransferAmountUiState.ValidAmountEnteredUiState.AmountEqualOrAboveBalanceUiState
      }
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
      cardModel = cardModel,
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

    val bottomSheetModel = when (sheetState) {
      SheetState.Hidden -> null
      SheetState.HardwareRequiredSheetState ->
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
     * Legacy informational sheet shown when fiat exchange rates are unavailable and the customer
     * would need hardware to continue.
     */
    data object HardwareRequiredSheetState : SheetState

    /** No sheet is currently displayed. */
    data object Hidden : SheetState
  }
}
