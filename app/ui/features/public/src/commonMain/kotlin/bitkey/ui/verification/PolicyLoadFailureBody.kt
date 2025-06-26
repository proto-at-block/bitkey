package bitkey.ui.verification

import androidx.compose.runtime.Composable
import build.wallet.analytics.events.screen.id.TxVerificationEventTrackerScreenId
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel

/**
 * Fatal error when loading the current Transaction Verification Policy.
 *
 * This error should never happen! This indicates that we are unable to load
 * any current policy information for the user.
 * This would likely indicate corrupted local database information.
 */
@Composable
internal fun PolicyLoadFailureBody(
  error: Error,
  onExit: () -> Unit,
) = ErrorFormBodyModel(
  eventTrackerScreenId = TxVerificationEventTrackerScreenId.POLICY_LOAD_FAILURE,
  errorData = ErrorData(
    segment = TxVerificationAppSegment.ManagePolicy,
    actionDescription = "Loading current verification threshold",
    cause = error
  ),
  title = "Unable to load Verification Status",
  primaryButton = ButtonDataModel(
    text = "Close",
    onClick = onExit
  )
)
