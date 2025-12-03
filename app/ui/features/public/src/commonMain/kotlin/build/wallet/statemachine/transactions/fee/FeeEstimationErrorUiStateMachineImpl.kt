package build.wallet.statemachine.transactions.fee

import androidx.compose.runtime.Composable
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.NetworkErrorFormBodyModel
import build.wallet.statemachine.send.fee.FeeSelectionEventTrackerScreenId.*

@BitkeyInject(ActivityScope::class)
class FeeEstimationErrorUiStateMachineImpl : FeeEstimationErrorUiStateMachine {
  @Composable
  override fun model(props: FeeEstimationErrorUiProps): BodyModel =
    when (val error = props.error) {
      FeeEstimationErrorUiError.InsufficientFunds ->
        errorForm(
          props = props,
          copy = props.context.copyFor(error),
          screenId = FEE_ESTIMATION_INSUFFICIENT_FUNDS_ERROR_SCREEN
        )

      FeeEstimationErrorUiError.SpendBelowDust ->
        errorForm(
          props = props,
          copy = props.context.copyFor(error),
          screenId = FEE_ESTIMATION_BELOW_DUST_LIMIT_ERROR_SCREEN
        )

      is FeeEstimationErrorUiError.LoadFailed ->
        NetworkErrorFormBodyModel(
          title = props.context.loadFeesTitle(),
          isConnectivityError = error.isConnectivityError,
          onRetry = props.onRetry,
          onBack = props.onBack,
          errorData = props.errorData,
          eventTrackerScreenId = FEE_ESTIMATION_LOAD_FEES_ERROR_SCREEN
        )

      FeeEstimationErrorUiError.FeeRateTooLow ->
        errorForm(
          props = props,
          copy = props.context.copyFor(error),
          screenId = FEE_ESTIMATION_FEE_RATE_TOO_LOW_ERROR_SCREEN
        )

      FeeEstimationErrorUiError.Generic ->
        errorForm(
          props = props,
          copy = props.context.copyFor(error),
          screenId = FEE_ESTIMATION_PSBT_CONSTRUCTION_ERROR_SCREEN
        )
    }

  private fun errorForm(
    props: FeeEstimationErrorUiProps,
    copy: FeeEstimationErrorCopy,
    screenId: EventTrackerScreenId?,
  ): BodyModel =
    ErrorFormBodyModel(
      title = copy.title,
      subline = copy.subline,
      primaryButton =
        ButtonDataModel(
          text = copy.primaryButtonText,
          onClick = props.onBack
        ),
      eventTrackerScreenId = screenId,
      errorData = props.errorData
    )
}
