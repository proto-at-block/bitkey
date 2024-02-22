package build.wallet.statemachine.trustedcontact.model

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.RetrieveTrustedContactInvitationErrorCode
import build.wallet.f8e.error.code.RetrieveTrustedContactInvitationErrorCode.NOT_FOUND
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.NetworkErrorFormBodyModel

fun RetrievingInviteWithF8eFailureBodyModel(
  onBack: () -> Unit,
  onRetry: () -> Unit,
  error: F8eError<RetrieveTrustedContactInvitationErrorCode>,
): BodyModel {
  val title = "We couldn’t retrieve the invitation associated with the provided code"
  val eventTrackerScreenId = TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE

  return when (error) {
    is F8eError.SpecificClientError -> {
      when (error.errorCode) {
        NOT_FOUND ->
          ErrorFormBodyModel(
            title = title,
            subline = "The provided code was incorrect. Please try again.",
            primaryButton = ButtonDataModel(text = "Back", onClick = onBack),
            eventTrackerScreenId = eventTrackerScreenId
          )
      }
    }

    else -> {
      val isConnectivityError = error is F8eError.ConnectivityError
      NetworkErrorFormBodyModel(
        title = title,
        isConnectivityError = isConnectivityError,
        onRetry = onRetry.takeIf { isConnectivityError },
        onBack = onBack,
        eventTrackerScreenId = eventTrackerScreenId
      )
    }
  }
}
