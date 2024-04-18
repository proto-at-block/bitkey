package build.wallet.statemachine.account.create.full

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.recovery.RecoverySegment

fun CloudBackupFailedScreenModel(
  eventTrackerScreenId: EventTrackerScreenId,
  error: Error,
  onTryAgain: () -> Unit,
) = ErrorFormBodyModel(
  onBack = null,
  title = "We couldn’t upload a cloud backup",
  subline = "Please retry.",
  primaryButton =
    ButtonDataModel(
      text = "Retry",
      onClick = onTryAgain
    ),
  eventTrackerScreenId = eventTrackerScreenId,
  errorData =
    ErrorData(
      segment = RecoverySegment.CloudBackup.FullAccount.Upload,
      actionDescription = "Uploading a full account cloud backup failed",
      cause = error
    )
)
