package bitkey.ui.verification

import androidx.compose.runtime.Composable
import build.wallet.analytics.events.screen.id.TxVerificationEventTrackerScreenId
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel

/**
 * Error screen shown when updating the Transaction Verification Policy fails.
 *
 * This occurs when the API call to update the threshold fails, typically due to
 * network issues or server errors. Unlike the policy load failure, this is a
 * recoverable error that the user can retry.
 */
@Composable
internal fun PolicyUpdateFailureBody(
  error: Error,
  onRetry: () -> Unit,
  onExit: () -> Unit,
) = ErrorFormBodyModel(
  eventTrackerScreenId = TxVerificationEventTrackerScreenId.POLICY_UPDATE_FAILURE,
  errorData = ErrorData(
    segment = TxVerificationAppSegment.ManagePolicy,
    actionDescription = "Updating transaction verification policy",
    cause = error
  ),
  title = "Unable to update your transaction verification policy",
  primaryButton = ButtonDataModel(
    text = "Retry",
    onClick = onRetry
  ),
  secondaryButton = ButtonDataModel(
    text = "Cancel",
    onClick = onExit
  )
)
