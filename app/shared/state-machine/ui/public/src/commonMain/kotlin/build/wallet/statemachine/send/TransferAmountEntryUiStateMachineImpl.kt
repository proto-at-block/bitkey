package build.wallet.statemachine.send

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.availability.NetworkReachability
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.SendAll
import build.wallet.bitkey.factor.SigningFactor
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.limit.DailySpendingLimitStatus.RequiresHardware
import build.wallet.limit.MobilePaySpendingPolicy
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import build.wallet.money.currency.Currency
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.data.mobilepay.MobilePayData.MobilePayEnabledData
import build.wallet.statemachine.data.money.convertedOrZeroWithRates
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiProps
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiStateMachine
import build.wallet.statemachine.send.TransferAmountEntryUiStateMachineImpl.TransferAmountUiState.InvalidAmountEnteredUiState.AmountBelowDustLimitUiState
import build.wallet.statemachine.send.TransferAmountEntryUiStateMachineImpl.TransferAmountUiState.InvalidAmountEnteredUiState.AmountWithZeroBalanceUiState
import build.wallet.statemachine.send.TransferAmountEntryUiStateMachineImpl.TransferAmountUiState.ValidAmountEnteredUiState
import build.wallet.statemachine.send.TransferAmountEntryUiStateMachineImpl.TransferAmountUiState.ValidAmountEnteredUiState.AmountBelowBalanceUiState
import build.wallet.statemachine.send.TransferAmountEntryUiStateMachineImpl.TransferAmountUiState.ValidAmountEnteredUiState.AmountEqualOrAboveBalanceUiState
import build.wallet.statemachine.send.TransferScreenBannerModel.AmountEqualOrAboveBalanceBannerModel
import build.wallet.statemachine.send.TransferScreenBannerModel.F8eUnavailableBannerModel
import build.wallet.statemachine.send.TransferScreenBannerModel.HardwareRequiredBannerModel

class TransferAmountEntryUiStateMachineImpl(
  private val currencyConverter: CurrencyConverter,
  private val moneyCalculatorUiStateMachine: MoneyCalculatorUiStateMachine,
  private val mobilePaySpendingPolicy: MobilePaySpendingPolicy,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
) : TransferAmountEntryUiStateMachine {
  @Composable
  @Suppress("CyclomaticComplexMethod")
  override fun model(props: TransferAmountEntryUiProps): ScreenModel {
    // Always start with the currency of the given amount as the primary currency
    // and the given fiat or BTC as secondary, whichever the amount isn't
    var currencyState by remember {
      mutableStateOf(
        CurrencyState(
          primaryCurrency = props.initialAmount.currency,
          secondaryCurrency = if (props.initialAmount is BitcoinMoney) props.fiatCurrency else BTC
        )
      )
    }

    var sheetState by remember {
      mutableStateOf<SheetState>(SheetState.Hidden)
    }

    var initialPrimaryAmount by remember {
      mutableStateOf(props.initialAmount)
    }

    val calculatorModel =
      moneyCalculatorUiStateMachine.model(
        props =
          MoneyCalculatorUiProps(
            inputAmountCurrency = currencyState.primaryCurrency,
            secondaryDisplayAmountCurrency = currencyState.secondaryCurrency,
            initialAmountInInputCurrency = initialPrimaryAmount,
            exchangeRates = props.exchangeRates
          )
      )

    val enteredBitcoinMoney: BitcoinMoney =
      if (calculatorModel.primaryAmount is BitcoinMoney) {
        calculatorModel.primaryAmount
      } else if (calculatorModel.secondaryAmount is BitcoinMoney) {
        calculatorModel.secondaryAmount
      } else {
        // We make a separate conversion call as fallback.
        convertedOrZeroWithRates(
          currencyConverter,
          calculatorModel.primaryAmount,
          BTC,
          props.exchangeRates ?: emptyImmutableList()
        ) as BitcoinMoney
      }

    // We don't have exchange rates, so we can't convert.
    val enteredFiatMoney: FiatMoney? =
      props.exchangeRates?.let {
        if (calculatorModel.primaryAmount is FiatMoney) {
          calculatorModel.primaryAmount
        } else if (calculatorModel.secondaryAmount is FiatMoney) {
          calculatorModel.secondaryAmount
        } else {
          // We make a separate conversion call as fallback.
          convertedOrZeroWithRates(
            currencyConverter,
            calculatorModel.primaryAmount,
            props.fiatCurrency,
            props.exchangeRates
          ) as FiatMoney
        }
      }

    val bitcoinBalance = props.accountData.transactionsData.balance
    val fiatBalance: FiatMoney? =
      when (props.exchangeRates) {
        null -> null
        else ->
          convertedOrZeroWithRates(
            currencyConverter,
            bitcoinBalance.total,
            props.fiatCurrency,
            props.exchangeRates
          ).rounded() as? FiatMoney
      }

    val fiatBalanceFormatted = fiatBalance?.let { moneyDisplayFormatter.format(it) }.orEmpty()
    val bitcoinBalanceFormatted =
      moneyDisplayFormatter.format(bitcoinBalance.total)
    val balancedFormatted =
      remember(fiatBalanceFormatted, bitcoinBalanceFormatted, currencyState) {
        when (currencyState.primaryCurrency) {
          BTC -> bitcoinBalanceFormatted
          else -> fiatBalanceFormatted
        }
      }

    // TODO(W-703): derive from BDK
    val dustLimit = BitcoinMoney.sats(546)

    val latestTransactions = props.accountData.transactionsData.transactions

    val spendingLimitStatus by remember(
      enteredBitcoinMoney,
      latestTransactions,
      props.accountData.mobilePayData
    ) {
      mutableStateOf(
        mobilePaySpendingPolicy.getDailySpendingLimitStatus(
          transactionAmount = enteredBitcoinMoney,
          latestTransactions = latestTransactions,
          mobilePayBalance =
            when (val mobilePayData = props.accountData.mobilePayData) {
              is MobilePayEnabledData -> mobilePayData.balance
              else -> null
            }
        )
      )
    }

    val requiresHardware by remember(spendingLimitStatus) {
      mutableStateOf(
        !enteredBitcoinMoney.isZero && spendingLimitStatus is RequiresHardware
      )
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
            when (currencyState.primaryCurrency) {
              BTC -> enteredBitcoinMoney >= bitcoinBalance.total
              else ->
                enteredFiatMoney?.let {
                  enteredFiatMoney >= fiatBalance
                } ?: error("Entered fiat money is null so it should not be primary currency. This should never happen.")
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
          bitcoinBalance.total.isZero ->
            AmountWithZeroBalanceUiState(
              disableAmount = !(enteredBitcoinMoney.isZero || (enteredFiatMoney?.isZero == true))
            )
          // Amount entered is above balance
          enteredAmountAboveBalance -> AmountEqualOrAboveBalanceUiState
          // Amount entered is below dust limit
          enteredBitcoinMoney < dustLimit -> AmountBelowDustLimitUiState

          // Transfer amount is within bounds of balance
          else -> AmountBelowBalanceUiState
        }
      }
    }

    val bannerModel by remember(
      transferAmountState,
      spendingLimitStatus.spendingLimit,
      fiatBalanceFormatted,
      requiresHardware
    ) {
      derivedStateOf {
        when {
          transferAmountState is AmountWithZeroBalanceUiState -> null
          transferAmountState is AmountEqualOrAboveBalanceUiState -> AmountEqualOrAboveBalanceBannerModel
          transferAmountState is AmountBelowBalanceUiState && requiresHardware -> HardwareRequiredBannerModel
          transferAmountState is AmountBelowBalanceUiState && props.f8eReachability == NetworkReachability.UNREACHABLE ->
            F8eUnavailableBannerModel
          else -> null
        }
      }
    }

    val requiredSigner by remember(transferAmountState, requiresHardware) {
      derivedStateOf {
        when (transferAmountState) {
          is ValidAmountEnteredUiState -> {
            if (requiresHardware) {
              SigningFactor.Hardware
            } else {
              SigningFactor.F8e
            }
          }

          else -> SigningFactor.F8e
        }
      }
    }
    val disableTransferAmount by remember(transferAmountState) {
      derivedStateOf {
        when (val state = transferAmountState) {
          is AmountWithZeroBalanceUiState -> state.disableAmount
          is AmountEqualOrAboveBalanceUiState -> true
          else -> false
        }
      }
    }

    val bodyModel =
      TransferAmountBodyModel(
        onBack = props.onBack,
        balanceTitle = "$balancedFormatted available",
        amountModel = calculatorModel.amountModel,
        keypadModel = calculatorModel.keypadModel,
        bannerModel = bannerModel,
        continueButtonEnabled = transferAmountState is AmountBelowBalanceUiState,
        amountDisabled = disableTransferAmount,
        onContinueClick = {
          if (transferAmountState is ValidAmountEnteredUiState) {
            props.onContinueClick(
              ContinueTransferParams(
                ExactAmount(enteredBitcoinMoney),
                enteredFiatMoney,
                requiredSigner,
                spendingLimitStatus.spendingLimit
              )
            )
          }
        },
        onSendMaxClick = {
          props.onContinueClick(
            ContinueTransferParams(
              SendAll,
              fiatBalance,
              requiredSigner,
              spendingLimitStatus.spendingLimit
            )
          )
        },
        onSwapCurrencyClick = {
          if (calculatorModel.secondaryAmount != null) {
            initialPrimaryAmount = calculatorModel.secondaryAmount

            currencyState =
              currencyState.copy(
                primaryCurrency = currencyState.secondaryCurrency,
                secondaryCurrency = currencyState.primaryCurrency
              )
          }
        },
        onHardwareRequiredClick = {
          sheetState =
            if (props.f8eReachability == NetworkReachability.UNREACHABLE) {
              SheetState.HardwareRequiredSheetState
            } else {
              SheetState.Hidden
            }
        }
      )

    val bottomSheetModel =
      when (sheetState) {
        is SheetState.Hidden -> null
        is SheetState.HardwareRequiredSheetState ->
          SheetModel(
            onClosed = { sheetState = SheetState.Hidden },
            body =
              ErrorFormBodyModel(
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

  private sealed interface TransferAmountUiState {
    sealed interface ValidAmountEnteredUiState : TransferAmountUiState {
      /** Amount is within limits and does not require hardware signing. */
      data object AmountBelowBalanceUiState : ValidAmountEnteredUiState

      /** Amount equal or above available funds. This is valid because we will send all. */
      data object AmountEqualOrAboveBalanceUiState : ValidAmountEnteredUiState
    }

    /** Invalid amount entered with Send Max feature flag turned on, not able to proceed. */
    sealed interface InvalidAmountEnteredUiState : TransferAmountUiState {
      /** User entered an amount while having a zero balance */
      data class AmountWithZeroBalanceUiState(
        val disableAmount: Boolean,
      ) : InvalidAmountEnteredUiState

      /** Amount is too small to send. */
      data object AmountBelowDustLimitUiState : InvalidAmountEnteredUiState
    }
  }

  private data class CurrencyState(
    val primaryCurrency: Currency,
    val secondaryCurrency: Currency,
  )

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
