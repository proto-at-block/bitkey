package build.wallet.statemachine.inheritance.claims.start

import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.inheritance.InheritanceAppSegment

/**
 * Error screen shown when the claim failed to start.
 */
internal fun ClaimFailedBodyModel(
  error: Throwable,
  tryAgain: () -> Unit,
  cancel: () -> Unit,
) = ErrorFormBodyModel(
  eventTrackerScreenId = InheritanceEventTrackerScreenId.SubmittingClaimFailed,
  errorData = ErrorData(
    segment = InheritanceAppSegment.BeneficiaryClaim.Start,
    actionDescription = "Starting inheritance claim",
    cause = error
  ),
  title = "Claim submission failed",
  primaryButton = ButtonDataModel(
    text = "Try again",
    onClick = tryAgain
  ),
  secondaryButton = ButtonDataModel(
    text = "Cancel",
    onClick = cancel
  )
)
