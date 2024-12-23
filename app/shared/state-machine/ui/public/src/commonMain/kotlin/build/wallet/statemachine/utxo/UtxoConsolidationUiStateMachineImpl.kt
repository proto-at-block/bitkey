package build.wallet.statemachine.utxo

import androidx.compose.runtime.*
import build.wallet.account.AccountService
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.UtxoConsolidationEventTrackerScreenId
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.toFormattedString
import build.wallet.bitcoin.utxo.NotEnoughUtxosToConsolidateError
import build.wallet.bitcoin.utxo.UtxoConsolidationParams
import build.wallet.bitcoin.utxo.UtxoConsolidationService
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.AmountDisplayText
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.money.formatter.amountDisplayText
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.LoadingSuccessBodyModel.State.Loading
import build.wallet.statemachine.data.money.convertedOrNull
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.utxo.UtxoConsolidationUiStateMachineImpl.State.*
import build.wallet.statemachine.utxo.UtxoConsolidationUiStateMachineImpl.State.ViewingConfirmation.SheetState.*
import build.wallet.time.DateTimeFormatter
import build.wallet.time.TimeZoneProvider
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime

@BitkeyInject(ActivityScope::class)
class UtxoConsolidationUiStateMachineImpl(
  private val accountService: AccountService,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val currencyConverter: CurrencyConverter,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val dateTimeFormatter: DateTimeFormatter,
  private val timeZoneProvider: TimeZoneProvider,
  private val utxoConsolidationService: UtxoConsolidationService,
  private val nfcSessionUiStateMachine: NfcSessionUIStateMachine,
) : UtxoConsolidationUiStateMachine {
  private val consolidationTimeExplanation =
    "We selected a 60-minute transfer target to get you the lowest network fees for consolidation."

  private val consolidationCostExplanation =
    "The number of UTXOs being consolidated and the current network fees determine the total cost. Bitkey never charges or receives fees for transfers."

  @Composable
  override fun model(props: UtxoConsolidationProps): ScreenModel {
    var state: State by remember { mutableStateOf(PreparingUtxoConsolidation) }

    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

    return when (val currentState = state) {
      is PreparingUtxoConsolidation -> {
        LaunchedEffect("prepare-utxo-consolidation") {
          val account = accountService.activeAccount().firstOrNull()
          if (account !is FullAccount) {
            props.onBack()
            return@LaunchedEffect
          }

          utxoConsolidationService.prepareUtxoConsolidation()
            .onSuccess { consolidationParamsList ->
              // TODO(W-9710): implement support for different consolidation types
              val consolidationParams = consolidationParamsList.single()
              if (consolidationParams.walletExceedsMaxUtxoCount) {
                state = ShowingExceedsMaxUtxoCount(account, consolidationParams)
              } else {
                state = ViewingConfirmation(account, consolidationParams)
              }
            }
            .onFailure { error ->
              state = when (error) {
                is NotEnoughUtxosToConsolidateError -> ShowingNotEnoughUtxosError(error.utxoCount)
                else -> ShowingErrorLoadingUtxoConsolidation(error)
              }
            }
        }

        return LoadingSuccessBodyModel(
          onBack = props.onBack,
          state = Loading,
          id = UtxoConsolidationEventTrackerScreenId.LOADING_UTXO_CONSOLIDATION_DETAILS
        ).asRootScreen()
      }
      is ShowingNotEnoughUtxosError -> when (currentState.utxoCount) {
        0 -> noUtxosToConsolidateErrorModel(onBack = props.onBack).asRootScreen()
        1 -> notEnoughUtxosToConsolidateErrorModel(onBack = props.onBack).asRootScreen()
        else -> error("Unexpected utxo count for ShowingNotEnoughUtxosError: ${currentState.utxoCount}")
      }

      is ViewingConfirmation -> {
        val balanceTitle = if (currentState.consolidationParams.walletExceedsMaxUtxoCount) {
          // If we're only consolidating a portion of the user's UTXOs, change the data row to reflect that.
          "Value of UTXOs"
        } else {
          "Wallet balance"
        }

        val balanceAmountDisplayText = moneyDisplayFormatter.amountDisplayText(
          bitcoinAmount = currentState.consolidationParams.balance,
          fiatAmount = convertedOrNull(
            converter = currencyConverter,
            fromAmount = currentState.consolidationParams.balance,
            toCurrency = fiatCurrency
          ) as FiatMoney?
        )

        val consolidationCostDisplayText = moneyDisplayFormatter.amountDisplayText(
          bitcoinAmount = currentState.consolidationParams.consolidationCost,
          fiatAmount = convertedOrNull(
            converter = currencyConverter,
            fromAmount = currentState.consolidationParams.consolidationCost,
            toCurrency = fiatCurrency
          ) as FiatMoney?
        )

        ScreenModel(
          body = UtxoConsolidationConfirmationModel(
            balanceTitle = balanceTitle,
            balanceAmountDisplayText = balanceAmountDisplayText,
            utxoCount = currentState.consolidationParams.eligibleUtxoCount.toString(),
            consolidationCostDisplayText = consolidationCostDisplayText,
            estimatedConsolidationTime = currentState.consolidationParams.transactionPriority.toFormattedString(),
            showUnconfirmedTransactionsCallout = currentState.consolidationParams.walletHasUnconfirmedUtxos,
            onBack = props.onBack,
            onContinue = {
              state = currentState.copy(
                sheetState = TapAndHoldToConsolidateSheet
              )
            },
            onConsolidationTimeClick = {
              state = currentState.copy(sheetState = ConsolidationTimeInfoSheet)
            },
            onConsolidationCostClick = {
              state = currentState.copy(sheetState = ConsolidationCostInfoSheet)
            }
          ),
          presentationStyle = ScreenPresentationStyle.Root,
          bottomSheetModel = currentState.sheetState.toBottomSheetModel(
            onBack = { state = currentState.copy(sheetState = Hidden) },
            onConsolidate = {
              state = SigningConsolidationWithHardware(
                account = currentState.account,
                consolidationParams = currentState.consolidationParams,
                consolidationCostDisplayText = consolidationCostDisplayText
              )
            }
          )
        )
      }
      is SigningConsolidationWithHardware -> {
        nfcSessionUiStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              commands.signTransaction(
                session = session,
                psbt = currentState.consolidationParams.appSignedPsbt,
                spendingKeyset = currentState.account.keybox.activeSpendingKeyset
              )
            },
            onSuccess = { appAndHardwareSignedPsbt ->
              state = BroadcastingConsolidationTransaction(
                account = currentState.account,
                consolidationParams = currentState.consolidationParams,
                appAndHardwareSignedPsbt = appAndHardwareSignedPsbt,
                consolidationCostDisplayText = currentState.consolidationCostDisplayText
              )
            },
            onCancel = {
              state = ViewingConfirmation(currentState.account, currentState.consolidationParams)
            },
            isHardwareFake = currentState.account.config.isHardwareFake,
            screenPresentationStyle = ScreenPresentationStyle.Root,
            eventTrackerContext = NfcEventTrackerScreenIdContext.UTXO_CONSOLIDATION_SIGN_TRANSACTION,
            shouldShowLongRunningOperation = true
          )
        )
      }
      is BroadcastingConsolidationTransaction -> {
        LaunchedEffect("broadcast-consolidation-transaction") {
          utxoConsolidationService
            .broadcastConsolidation(currentState.appAndHardwareSignedPsbt)
            .onSuccess { broadcastDetail ->
              state = ShowingSuccessScreen(
                consolidationParams = currentState.consolidationParams,
                arrivalTime = broadcastDetail.estimatedConfirmationTime,
                consolidationCostDisplayText = currentState.consolidationCostDisplayText
              )
            }
            .onFailure {
              state = ShowingErrorBroadcastingConsolidation(
                error = it,
                account = currentState.account,
                consolidationParams = currentState.consolidationParams
              )
            }
        }
        LoadingSuccessBodyModel(
          onBack = null,
          state = Loading,
          id = UtxoConsolidationEventTrackerScreenId.BROADCASTING_UTXO_CONSOLIDATION
        ).asRootScreen()
      }
      is ShowingSuccessScreen -> {
        UtxoConsolidationTransactionSentModel(
          targetAddress = currentState.consolidationParams.targetAddress.chunkedAddress(),
          arrivalTime = dateTimeFormatter.shortDateWithTime(
            localDateTime = currentState.arrivalTime.toLocalDateTime(timeZoneProvider.current())
          ),
          utxosCountConsolidated = "${currentState.consolidationParams.eligibleUtxoCount} → 1",
          consolidationCostDisplayText = currentState.consolidationCostDisplayText,
          onBack = props.onConsolidationSuccess,
          onDone = props.onConsolidationSuccess
        ).asRootScreen()
      }
      is ShowingErrorLoadingUtxoConsolidation -> ErrorFormBodyModel(
        title = "Let’s try that again",
        subline = "It looks like something went wrong behind the scenes. Please try again.",
        primaryButton = ButtonDataModel(
          text = "Ok",
          onClick = props.onBack
        ),
        onBack = props.onBack,
        eventTrackerScreenId = null
      ).asRootScreen()
      is ShowingErrorBroadcastingConsolidation -> ErrorFormBodyModel(
        title = "Error completing consolidation",
        subline = "It looks like something went wrong while consolidating. Please try again",
        primaryButton = ButtonDataModel(
          text = "Try again",
          onClick = {
            state = ViewingConfirmation(
              account = currentState.account,
              consolidationParams = currentState.consolidationParams
            )
          }
        ),
        onBack = props.onBack,
        eventTrackerScreenId = null
      ).asRootScreen()
      is ShowingExceedsMaxUtxoCount -> ExceedsMaxUtxoCountBodyModel(
        onBack = props.onBack,
        maxUtxoCount = currentState.consolidationParams.maxUtxoCount,
        onContinue = {
          state = ViewingConfirmation(
            account = currentState.account,
            consolidationParams = currentState.consolidationParams
          )
        }
      ).asRootScreen()
    }
  }

  private fun ViewingConfirmation.SheetState.toBottomSheetModel(
    onBack: () -> Unit,
    onConsolidate: () -> Unit,
  ): SheetModel? {
    return when (this) {
      ConsolidationCostInfoSheet -> consolidationInfoSheetModel(
        eventTrackerScreenId = UtxoConsolidationEventTrackerScreenId.UTXO_CONSOLIDATION_COST_INFO,
        title = "Consolidation cost",
        explainer = consolidationCostExplanation,
        onBack = onBack
      )
      ConsolidationTimeInfoSheet -> consolidationInfoSheetModel(
        eventTrackerScreenId = UtxoConsolidationEventTrackerScreenId.UTXO_CONSOLIDATION_TIME_INFO,
        title = "Time to consolidate",
        explainer = consolidationTimeExplanation,
        onBack = onBack
      )
      Hidden -> null
      TapAndHoldToConsolidateSheet -> TapAndHoldToConsolidateUtxosBodyModel(
        onBack = onBack,
        onConsolidate = onConsolidate
      ).asSheetModalScreen(onBack)
    }
  }

  private sealed interface State {
    data object PreparingUtxoConsolidation : State

    data class ShowingNotEnoughUtxosError(
      val utxoCount: Int,
    ) : State

    data class ShowingErrorLoadingUtxoConsolidation(
      val error: Throwable,
    ) : State

    data class ViewingConfirmation(
      val account: FullAccount,
      val consolidationParams: UtxoConsolidationParams,
      val sheetState: SheetState = Hidden,
    ) : State {
      /**
       * State representing whether we're showing an informational half-sheet.
       */
      sealed interface SheetState {
        data object Hidden : SheetState

        data object ConsolidationCostInfoSheet : SheetState

        data object ConsolidationTimeInfoSheet : SheetState

        data object TapAndHoldToConsolidateSheet : SheetState
      }
    }

    data class SigningConsolidationWithHardware(
      val account: FullAccount,
      val consolidationParams: UtxoConsolidationParams,
      val consolidationCostDisplayText: AmountDisplayText,
    ) : State

    data class BroadcastingConsolidationTransaction(
      val account: FullAccount,
      val consolidationParams: UtxoConsolidationParams,
      val appAndHardwareSignedPsbt: Psbt,
      val consolidationCostDisplayText: AmountDisplayText,
    ) : State

    data class ShowingSuccessScreen(
      val consolidationParams: UtxoConsolidationParams,
      val arrivalTime: Instant,
      val consolidationCostDisplayText: AmountDisplayText,
    ) : State

    data class ShowingErrorBroadcastingConsolidation(
      val error: Throwable,
      val account: FullAccount,
      val consolidationParams: UtxoConsolidationParams,
    ) : State

    data class ShowingExceedsMaxUtxoCount(
      val account: FullAccount,
      val consolidationParams: UtxoConsolidationParams,
    ) : State
  }
}
