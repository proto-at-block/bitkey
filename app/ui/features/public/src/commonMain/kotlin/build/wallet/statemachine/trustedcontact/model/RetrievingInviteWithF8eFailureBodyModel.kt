package build.wallet.statemachine.trustedcontact.model

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.RetrieveTrustedContactInvitationErrorCode.INVITATION_ROLE_MISMATCH
import bitkey.f8e.error.code.RetrieveTrustedContactInvitationErrorCode.NOT_FOUND
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE
import build.wallet.relationships.RetrieveInvitationCodeError
import build.wallet.statemachine.core.*
import build.wallet.statemachine.recovery.RecoverySegment

fun RetrievingInviteWithF8eFailureBodyModel(
  onBack: () -> Unit,
  onRetry: () -> Unit,
  error: RetrieveInvitationCodeError,
  variant: TrustedContactFeatureVariant,
): BodyModel {
  val title = "We couldn’t retrieve the invitation associated with the provided code"
  val eventTrackerScreenId = TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE

  return when (error) {
    is RetrieveInvitationCodeError.InvalidInvitationCode ->
      ErrorFormBodyModel(
        title = title,
        subline = "The provided code was incorrect. Please try again.",
        primaryButton = ButtonDataModel(text = "Back", onClick = onBack),
        eventTrackerScreenId = eventTrackerScreenId,
        errorData = ErrorData(
          segment = RecoverySegment.SocRec.TrustedContact.Setup,
          actionDescription = "Parsing Invitation Code",
          cause = error.cause
        )
      )
    is RetrieveInvitationCodeError.ExpiredInvitationCode ->
      ErrorFormBodyModel(
        title = "This code has expired",
        subline = "The invite code you entered has expired. Reach out to your contact to request a new code.",
        primaryButton = ButtonDataModel(text = "Got it", onClick = onBack),
        eventTrackerScreenId = eventTrackerScreenId,
        errorData = ErrorData(
          segment = RecoverySegment.SocRec.TrustedContact.Setup,
          actionDescription = "Invitation expiration validation",
          cause = error.cause
        )
      )
    is RetrieveInvitationCodeError.InvitationCodeVersionMismatch ->
      ErrorFormBodyModel(
        title = "Bitkey app out of date",
        subline = "The invite could not be accepted - please make sure both you and the sender have updated to the most recent Bitkey app version, and then try again.",
        primaryButton = ButtonDataModel(text = "Back", onClick = onBack),
        errorData = ErrorData(
          segment = RecoverySegment.SocRec.TrustedContact.Setup,
          cause = error.cause,
          actionDescription = "Invitation Data parsing"
        ),
        eventTrackerScreenId = eventTrackerScreenId
      )
    is RetrieveInvitationCodeError.F8ePropagatedError -> {
      when (val f8eError = error.error) {
        is F8eError.SpecificClientError -> {
          when (f8eError.errorCode) {
            INVITATION_ROLE_MISMATCH ->
              ErrorFormBodyModel(
                title = "This code is for a different invitation type",
                subline = when (variant) {
                  is TrustedContactFeatureVariant.Generic -> "Please verify the invitation code and try again."
                  is TrustedContactFeatureVariant.Direct -> when (variant.target) {
                    TrustedContactFeatureVariant.Feature.Inheritance -> "Navigate to Security Hub > Recovery Contacts > Accept invite and try again."
                    TrustedContactFeatureVariant.Feature.Recovery -> "Navigate to Settings > Inheritance > Benefactors > Accept invite and try again."
                  }
                },
                primaryButton = ButtonDataModel(text = "Back", onClick = onBack),
                eventTrackerScreenId = eventTrackerScreenId,
                errorData = ErrorData(
                  segment = RecoverySegment.SocRec.TrustedContact.Setup,
                  cause = error.cause,
                  actionDescription = "Invitation code <-> role mismatch"
                )
              )
            NOT_FOUND ->
              ErrorFormBodyModel(
                title = title,
                subline = "The provided code was incorrect. Please try again.",
                primaryButton = ButtonDataModel(text = "Back", onClick = onBack),
                eventTrackerScreenId = eventTrackerScreenId,
                errorData = ErrorData(
                  segment = RecoverySegment.SocRec.TrustedContact.Setup,
                  cause = error.cause,
                  actionDescription = "Retrieving Invitation from F8e"
                )
              )
          }
        }

        else -> {
          val isConnectivityError = f8eError is F8eError.ConnectivityError
          NetworkErrorFormBodyModel(
            title = title,
            isConnectivityError = isConnectivityError,
            onRetry = onRetry.takeIf { isConnectivityError },
            onBack = onBack,
            eventTrackerScreenId = eventTrackerScreenId,
            errorData = ErrorData(
              segment = RecoverySegment.SocRec.TrustedContact.Setup,
              cause = error.cause,
              actionDescription = "Retrieving Invitation from F8e"
            )
          )
        }
      }
    }
  }
}
