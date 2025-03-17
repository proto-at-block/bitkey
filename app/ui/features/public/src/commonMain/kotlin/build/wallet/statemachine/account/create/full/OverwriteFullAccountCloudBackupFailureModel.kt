package build.wallet.statemachine.account.create.full

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

fun OverwriteFullAccountCloudBackupFailureModel(
  onBack: () -> Unit,
  onRetry: () -> Unit,
) = ErrorFormBodyModel(
  title = "Failed to delete account",
  toolbar =
    ToolbarModel(
      leadingAccessory = BackAccessory(onBack)
    ),
  onBack = onBack,
  primaryButton =
    ButtonDataModel(
      text = "Retry",
      onClick = onRetry
    ),
  eventTrackerScreenId = CloudEventTrackerScreenId.FAILURE_DELETING_FULL_ACCOUNT
)
