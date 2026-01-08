package build.wallet.statemachine.trustedcontact.model

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CreateTrustedContactInvitationErrorCode.MAX_TRUSTED_CONTACTS_REACHED
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_BENEFICIARY_ENROLLMENT_CREATE_INVITE_FAILURE
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_CREATE_INVITE_FAILURE
import build.wallet.relationships.CreateInvitationError
import build.wallet.statemachine.core.*
import build.wallet.statemachine.inheritance.InheritanceAppSegment
import build.wallet.statemachine.recovery.RecoverySegment

fun CreatingInviteWithF8eFailureBodyModel(
  isInheritance: Boolean,
  onBack: () -> Unit,
  onRetry: () -> Unit,
  error: CreateInvitationError,
): BodyModel {
  val subject = if (isInheritance) "beneficiary" else "Recovery Contact"
  val title = "Unable to save $subject"
  val segment = if (isInheritance) {
    InheritanceAppSegment.Benefactor.Invite
  } else {
    RecoverySegment.SocRec.TrustedContact.Setup
  }
  val eventTrackerScreenId = if (isInheritance) {
    TC_BENEFICIARY_ENROLLMENT_CREATE_INVITE_FAILURE
  } else {
    TC_ENROLLMENT_CREATE_INVITE_FAILURE
  }

  return when (error) {
    is CreateInvitationError.F8ePropagatedError -> {
      when (val f8eError = error.error) {
        is F8eError.SpecificClientError -> {
          when (f8eError.errorCode) {
            MAX_TRUSTED_CONTACTS_REACHED -> maxTrustedContactsReachedErrorModel(
              isInheritance = isInheritance,
              segment = segment,
              onBack = onBack,
              error = error,
              eventTrackerScreenId = eventTrackerScreenId
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
            errorData = ErrorData(
              segment = segment,
              cause = error.error.error,
              actionDescription = "Creating trusted contact invitation - network error"
            ),
            eventTrackerScreenId = eventTrackerScreenId
          )
        }
      }
    }
    is CreateInvitationError.LocalCryptoError,
    is CreateInvitationError.DatabaseError,
    is CreateInvitationError.InviteCodeBuildError,
    is CreateInvitationError.AccountRetrievalError,
    -> {
      NetworkErrorFormBodyModel(
        title = title,
        isConnectivityError = false,
        onRetry = onRetry,
        onBack = onBack,
        errorData = ErrorData(
          segment = segment,
          cause = error.cause,
          actionDescription = "Creating trusted contact invitation - ${error::class.simpleName}"
        ),
        eventTrackerScreenId = eventTrackerScreenId
      )
    }
  }
}

private fun maxTrustedContactsReachedErrorModel(
  isInheritance: Boolean,
  segment: AppSegment,
  onBack: () -> Unit,
  error: CreateInvitationError.F8ePropagatedError,
  eventTrackerScreenId: SocialRecoveryEventTrackerScreenId,
): BodyModel {
  val sublineText = if (isInheritance) {
    "Currently, you can only have one Beneficiary. To add a new one, remove your current Beneficiary first."
  } else {
    "You can have up to 3 Recovery Contacts. To add a new one, remove one of your current contacts first."
  }
  return ErrorFormBodyModel(
    title = "Maximum limit reached",
    subline = sublineText,
    primaryButton = ButtonDataModel(text = "Got it", onClick = onBack),
    eventTrackerScreenId = eventTrackerScreenId,
    errorData = ErrorData(
      segment = segment,
      cause = error.error.error,
      actionDescription = "Creating trusted contact invitation - max trusted contacts reached"
    )
  )
}
