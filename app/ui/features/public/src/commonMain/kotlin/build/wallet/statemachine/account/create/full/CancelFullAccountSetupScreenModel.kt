package build.wallet.statemachine.account.create.full

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel

fun CancelFullAccountSetupScreenModel(onRetry: () -> Unit) =
  ErrorFormBodyModel(
    onBack = null,
    title = "We couldn’t cancel setup.",
    subline = "Please retry.",
    primaryButton =
      ButtonDataModel(
        text = "Retry",
        onClick = onRetry
      ),
    eventTrackerScreenId = CloudEventTrackerScreenId.FAILURE_RESTORE_FROM_LITE_ACCOUNT_CLOUD_BACKUP_AFTER_ONBOARDING,
    errorData = ErrorData(
      segment = OnboardingAppSegment.LiteToFullAccountUpgrade,
      actionDescription = "Canceling full account setup",
      cause = IllegalStateException("Failed to cancel full account setup")
    )
  )
