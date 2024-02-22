package build.wallet.statemachine.recovery.conflict.model

import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_LOADING
import build.wallet.statemachine.core.NetworkErrorFormBodyModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.RenderContext

fun CancelingSomeoneElsesRecoveryFailedSheetModel(
  onClose: () -> Unit,
  onRetry: () -> Unit,
) = SheetModel(
  onClosed = onClose,
  body =
    NetworkErrorFormBodyModel(
      title = "We couldn’t cancel the recovery",
      isConnectivityError = false,
      onRetry = onRetry,
      onBack = onClose,
      eventTrackerScreenId = LOST_HW_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_LOADING,
      renderContext = RenderContext.Sheet
    )
)
