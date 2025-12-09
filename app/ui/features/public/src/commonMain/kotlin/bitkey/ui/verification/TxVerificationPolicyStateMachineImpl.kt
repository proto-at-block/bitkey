package bitkey.ui.verification

import androidx.compose.runtime.*
import bitkey.verification.TxVerificationPolicy
import bitkey.verification.TxVerificationService
import bitkey.verification.VerificationThreshold
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.ExchangeRateService
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.*
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiProps
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiStateMachine
import build.wallet.time.MinimumLoadingDuration
import build.wallet.time.withMinimumDelay
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.toImmutableList

@BitkeyInject(ActivityScope::class)
class TxVerificationPolicyStateMachineImpl(
  private val txVerificationService: TxVerificationService,
  private val formatter: MoneyDisplayFormatter,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val moneyInputStateMachine: MoneyCalculatorUiStateMachine,
  private val exchangeRateService: ExchangeRateService,
  private val minimumLoadingDuration: MinimumLoadingDuration,
  private val hwProofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
) : TxVerificationPolicyStateMachine {
  @Composable
  override fun model(props: TxVerificationPolicyProps): ScreenModel {
    var viewState: State by remember { mutableStateOf(State.Overview) }
    val thresholdState by remember {
      txVerificationService.getCurrentThreshold()
    }.collectAsState(null)
    val fiatCurrency by remember {
      fiatCurrencyPreferenceRepository.fiatCurrencyPreference
    }.collectAsState()
    val exchangeRates by remember {
      exchangeRateService.exchangeRates
    }.collectAsState()

    when (val current = viewState) {
      is State.Updating -> LaunchedEffect("Update Policy") {
        val result = withMinimumDelay(minimumLoadingDuration.value) {
          txVerificationService.updateThreshold(
            policy = TxVerificationPolicy.Active(current.threshold),
            amountBtc = current.amountBtc,
            hwFactorProofOfPossession = current.hwFactorProofOfPossession
          )
        }
        result.onSuccess { policy ->
          viewState = when (policy) {
            is TxVerificationPolicy.Active -> State.Overview
            is TxVerificationPolicy.Pending -> State.AwaitingApproval(
              policy = policy,
              threshold = current.threshold,
              amountBtc = current.amountBtc,
              hwFactorProofOfPossession = current.hwFactorProofOfPossession
            )
          }
        }.onFailure { error ->
          viewState = State.UpdateError(
            error = error,
            threshold = current.threshold,
            amountBtc = current.amountBtc,
            hwFactorProofOfPossession = current.hwFactorProofOfPossession
          )
        }
      }
      else -> {}
    }

    val onToggleVerification: (Boolean) -> Unit = { enable ->
      viewState = if (enable) {
        State.ChooseEnabledType
      } else {
        State.HardwareConfirmation(
          threshold = VerificationThreshold.Disabled,
          amountBtc = null
        )
      }
    }

    return when (val current = viewState) {
      State.Overview -> overviewModel(
        props = props,
        thresholdState = thresholdState,
        onToggle = onToggleVerification
      ).asRootScreen()
      State.ChooseEnabledType -> overviewModel(
        props = props,
        thresholdState = thresholdState,
        onToggle = onToggleVerification,
        pendingToggleState = true
      ).asRootScreen(
        bottomSheetModel = ChooseTxPolicyTypeSheet(
          onClose = {
            viewState = State.Overview
          },
          onAlwaysClick = {
            viewState = State.HardwareConfirmation(
              threshold = VerificationThreshold.Always,
              amountBtc = BitcoinMoney.zero()
            )
          },
          onAboveAmountClick = {
            viewState = State.EnterAmount
          }
        )
      )
      State.EnterAmount -> overviewModel(
        props = props,
        thresholdState = thresholdState,
        onToggle = onToggleVerification,
        pendingToggleState = true
      ).asRootScreen(
        bottomSheetModel = SheetModel(
          size = SheetSize.FULL,
          onClosed = { viewState = State.ChooseEnabledType },
          body = moneyInputStateMachine.model(
            props = MoneyCalculatorUiProps(
              inputAmountCurrency = fiatCurrency,
              secondaryDisplayAmountCurrency = BTC,
              initialAmountInInputCurrency = FiatMoney.zero(fiatCurrency),
              exchangeRates = exchangeRates.toImmutableList()
            )
          ).let { moneyInputModel ->
            VerificationThresholdPickerModel(
              onBack = { viewState = State.ChooseEnabledType },
              onConfirmClick = {
                viewState = State.HardwareConfirmation(
                  threshold = VerificationThreshold.Enabled(
                    amount = moneyInputModel.primaryAmount
                  ),
                  amountBtc = moneyInputModel.secondaryAmount as BitcoinMoney
                )
              },
              model = moneyInputModel
            )
          }
        )
      )
      is State.HardwareConfirmation -> hwProofOfPossessionNfcStateMachine.model(
        props = ProofOfPossessionNfcProps(
          request = Request.HwKeyProof(
            onSuccess = { hwFactorProofOfPossession ->
              viewState = State.Updating(
                threshold = current.threshold,
                amountBtc = current.amountBtc,
                hwFactorProofOfPossession = hwFactorProofOfPossession
              )
            }
          ),
          fullAccountId = props.account.accountId,
          screenPresentationStyle = ScreenPresentationStyle.Modal,
          onBack = { viewState = State.Overview }
        )
      )
      is State.Updating -> overviewModel(
        props = props,
        thresholdState = thresholdState,
        onToggle = onToggleVerification,
        pendingToggleState = current.threshold is VerificationThreshold
      ).asRootScreen(
        bottomSheetModel = UpdatingPolicySheet(
          onBack = {
            viewState = State.Overview
          }
        )
      )
      is State.AwaitingApproval -> ApproveLimitBodyModel(
        onResendEmail = {
          viewState = State.HardwareConfirmation(
            threshold = current.threshold,
            amountBtc = current.amountBtc
          )
        },
        onCancel = {
          viewState = State.Overview
        }
      ).asRootScreen()
      is State.UpdateError -> PolicyUpdateFailureBody(
        error = current.error,
        onRetry = {
          viewState = State.Updating(
            threshold = current.threshold,
            amountBtc = current.amountBtc,
            hwFactorProofOfPossession = current.hwFactorProofOfPossession
          )
        },
        onExit = {
          viewState = State.Overview
        }
      ).asRootScreen()
    }
  }

  @Composable
  private fun overviewModel(
    props: TxVerificationPolicyProps,
    thresholdState: Result<VerificationThreshold?, Error>? = null,
    onToggle: (Boolean) -> Unit,
    pendingToggleState: Boolean? = null,
  ): BodyModel {
    // Use a disabled screen as a placeholder while loading:
    val loadingPlaceholder = TxVerificationPolicyStateModel(
      formatter = formatter,
      checked = false,
      enabled = false,
      onBack = props.onExit,
      updatePolicy = {}
    )

    thresholdState?.onSuccess { threshold ->
      return TxVerificationPolicyStateModel(
        formatter = formatter,
        onBack = props.onExit,
        threshold = threshold,
        checked = pendingToggleState ?: (threshold is VerificationThreshold),
        enabled = pendingToggleState == null,
        updatePolicy = onToggle
      )
    }

    thresholdState?.onFailure { error ->
      return PolicyLoadFailureBody(
        error = error,
        onExit = props.onExit
      )
    }

    return loadingPlaceholder
  }

  private sealed interface State {
    /**
     * Displays the current Transaction Verification status and actions to toggle/modify it.
     */
    data object Overview : State

    /**
     * User is choosing which type of transaction verification to enable, Always or Above Amount.
     */
    data object ChooseEnabledType : State

    /**
     * Keypad for the user to specify the amount above which transaction verification is required.
     */
    data object EnterAmount : State

    /**
     * Waiting screen displayed while waiting for an out-of-band approval to complete.
     */
    data class AwaitingApproval(
      val policy: TxVerificationPolicy.Pending,
      val threshold: VerificationThreshold,
      val amountBtc: BitcoinMoney?,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
    ) : State

    /**
     * Hardware proof-of-possession confirmation screen.
     */
    data class HardwareConfirmation(
      val threshold: VerificationThreshold,
      val amountBtc: BitcoinMoney?,
    ) : State

    /**
     * Loading indicator while the transaction verification policy is being updated on F8e.
     */
    data class Updating(
      val threshold: VerificationThreshold,
      val amountBtc: BitcoinMoney?,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
    ) : State

    /**
     * Error screen displayed when the policy update fails.
     */
    data class UpdateError(
      val error: Error,
      val threshold: VerificationThreshold,
      val amountBtc: BitcoinMoney?,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
    ) : State
  }
}
