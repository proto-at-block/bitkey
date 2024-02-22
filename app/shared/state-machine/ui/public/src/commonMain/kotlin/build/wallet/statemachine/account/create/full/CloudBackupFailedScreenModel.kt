package build.wallet.statemachine.account.create.full

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel

fun CloudBackupFailedScreenModel(
  eventTrackerScreenId: EventTrackerScreenId,
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
  eventTrackerScreenId = eventTrackerScreenId
)
