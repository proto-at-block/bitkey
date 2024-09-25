package build.wallet.statemachine.utxo

import androidx.compose.runtime.*
import build.wallet.account.AccountService
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.UtxoConsolidationEventTrackerScreenId
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.utxo.NotEnoughUtxosToConsolidateError
import build.wallet.bitcoin.utxo.UtxoConsolidationParams
import build.wallet.bitcoin.utxo.UtxoConsolidationService
import build.wallet.bitkey.account.FullAccount
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.LoadingSuccessBodyModel.State.Loading
import build.wallet.statemachine.core.LoadingSuccessBodyModel.State.Success
import build.wallet.statemachine.data.money.convertedOrZero
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
    "We selected a 24-hour transfer target to get you the lowest network fees for consolidation."

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
            .onSuccess { consolidationParams ->
              // TODO(W-9710): implement support for different consolidation types
              state = ViewingConfirmation(account, consolidationParams.single())
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
          state = Success,
          id = UtxoConsolidationEventTrackerScreenId.LOADING_UTXO_CONSOLIDATION_DETAILS
        ).asRootScreen()
      }
      is ShowingNotEnoughUtxosError -> when (currentState.utxoCount) {
        0 -> noUtxosToConsolidateErrorModel(onBack = props.onBack).asRootScreen()
        1 -> notEnoughUtxosToConsolidateErrorModel(onBack = props.onBack).asRootScreen()
        else -> error("Unexpected utxo count for ShowingNotEnoughUtxosError: ${currentState.utxoCount}")
      }

      is ViewingConfirmation -> {
        val balanceBitcoinString =
          moneyDisplayFormatter.format(currentState.consolidationParams.balance)
        val balanceFiat = convertedOrZero(
          converter = currencyConverter,
          fromAmount = currentState.consolidationParams.balance,
          toCurrency = fiatCurrency
        )
        val balanceFiatString = moneyDisplayFormatter.format(balanceFiat)

        val consolidationCostBitcoinString =
          moneyDisplayFormatter.format(currentState.consolidationParams.consolidationCost)
        val consolidationCostFiat = convertedOrZero(
          converter = currencyConverter,
          fromAmount = currentState.consolidationParams.consolidationCost,
          toCurrency = fiatCurrency
        )
        val consolidationCostFiatString = moneyDisplayFormatter.format(consolidationCostFiat)

        ScreenModel(
          body = utxoConsolidationConfirmationModel(
            balanceFiat = balanceFiatString,
            balanceBitcoin = balanceBitcoinString,
            utxoCount = currentState.consolidationParams.currentUtxoCount.toString(),
            consolidationCostFiat = consolidationCostFiatString,
            consolidationCostBitcoin = consolidationCostBitcoinString,
            onBack = props.onBack,
            onConfirmClick = {
              state = SigningConsolidationWithHardware(
                account = currentState.account,
                consolidationParams = currentState.consolidationParams
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
          bottomSheetModel = currentState.sheetState.toBottomSheetModel {
            state = currentState.copy(sheetState = Hidden)
          }
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
                appAndHardwareSignedPsbt = appAndHardwareSignedPsbt
              )
            },
            onCancel = {
              state = ViewingConfirmation(currentState.account, currentState.consolidationParams)
            },
            isHardwareFake = currentState.account.config.isHardwareFake,
            screenPresentationStyle = ScreenPresentationStyle.Root,
            eventTrackerContext = NfcEventTrackerScreenIdContext.UTXO_CONSOLIDATION_SIGN_TRANSACTION
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
                arrivalTime = broadcastDetail.estimatedConfirmationTime
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
        val consolidationCostBitcoinString =
          moneyDisplayFormatter.format(currentState.consolidationParams.consolidationCost)
        val consolidationCostFiat = convertedOrZero(
          converter = currencyConverter,
          fromAmount = currentState.consolidationParams.consolidationCost,
          toCurrency = fiatCurrency
        )
        val consolidationCostFiatString = moneyDisplayFormatter.format(consolidationCostFiat)

        utxoConsolidationTransactionSentModel(
          targetAddress = currentState.consolidationParams.targetAddress.chunkedAddress(),
          arrivalTime = dateTimeFormatter.shortDateWithTime(
            localDateTime = currentState.arrivalTime.toLocalDateTime(timeZoneProvider.current())
          ),
          utxosCountConsolidated = "${currentState.consolidationParams.currentUtxoCount} → 1",
          consolidationCostBitcoin = consolidationCostBitcoinString,
          consolidationCostFiat = consolidationCostFiatString,
          onBack = props.onConsolidationSuccess,
          onDone = props.onConsolidationSuccess
        ).asRootScreen()
      }
      is ShowingErrorLoadingUtxoConsolidation -> ErrorFormBodyModel(
        title = "Error showing UTXO consolidation",
        subline = "Something went wrong while loading UTXO consolidation feature",
        primaryButton = ButtonDataModel(
          text = "Go Back",
          onClick = props.onBack
        ),
        onBack = props.onBack,
        eventTrackerScreenId = null
      ).asRootScreen()
      is ShowingErrorBroadcastingConsolidation -> ErrorFormBodyModel(
        title = "Error completing consolidation",
        subline = "Something went wrong while broadcasting the consolidation transaction. Please try again.",
        primaryButton = ButtonDataModel(
          text = "Go Back",
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
    }
  }

  private fun ViewingConfirmation.SheetState.toBottomSheetModel(onBack: () -> Unit): SheetModel? {
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
      }
    }

    data class SigningConsolidationWithHardware(
      val account: FullAccount,
      val consolidationParams: UtxoConsolidationParams,
    ) : State

    data class BroadcastingConsolidationTransaction(
      val account: FullAccount,
      val consolidationParams: UtxoConsolidationParams,
      val appAndHardwareSignedPsbt: Psbt,
    ) : State

    data class ShowingSuccessScreen(
      val consolidationParams: UtxoConsolidationParams,
      val arrivalTime: Instant,
    ) : State

    data class ShowingErrorBroadcastingConsolidation(
      val error: Throwable,
      val account: FullAccount,
      val consolidationParams: UtxoConsolidationParams,
    ) : State
  }
}
