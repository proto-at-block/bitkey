package build.wallet.statemachine.trustedcontact.model

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.AcceptTrustedContactInvitationErrorCode.*
import bitkey.relationships.Relationships
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.platform.device.DevicePlatform
import build.wallet.relationships.AcceptInvitationCodeError
import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.NetworkErrorFormBodyModel
import build.wallet.statemachine.inheritance.InheritanceAppSegment
import build.wallet.statemachine.recovery.RecoverySegment

private const val ACTION_DESCRIPTION = "Accepting trusted contact invitation"

fun AcceptingInviteWithF8eFailureBodyModel(
  isInheritance: Boolean,
  onBack: () -> Unit,
  onRetry: () -> Unit,
  devicePlatform: DevicePlatform,
  error: AcceptInvitationCodeError,
  currentRelationships: Relationships,
): BodyModel {
  val subject = if (isInheritance) "beneficiary" else "Recovery Contact"
  var title = "We couldn’t complete your enrollment as a $subject"
  val eventTrackerScreenId = TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE
  val segment = if (isInheritance) {
    InheritanceAppSegment.Benefactor.Invite
  } else {
    RecoverySegment.SocRec.TrustedContact.Setup
  }

  return when (error) {
    is AcceptInvitationCodeError.InvalidInvitationCode ->
      ErrorFormBodyModel(
        title = title,
        subline = "The provided code was incorrect. Please try again.",
        primaryButton = ButtonDataModel(text = "Back", onClick = onBack),
        eventTrackerScreenId = eventTrackerScreenId,
        errorData = ErrorData(
          segment = segment,
          cause = Error("Invalid invitation code"),
          actionDescription = ACTION_DESCRIPTION
        )
      )
    is AcceptInvitationCodeError.CryptoError ->
      ErrorFormBodyModel(
        title = title,
        subline = "The provided code was incorrect. Please try again.",
        primaryButton = ButtonDataModel(text = "Back", onClick = onBack),
        eventTrackerScreenId = eventTrackerScreenId,
        errorData = ErrorData(
          segment = segment,
          cause = error.cause,
          actionDescription = ACTION_DESCRIPTION
        )
      )
    is AcceptInvitationCodeError.F8ePropagatedError -> {
      when (val f8eError = error.error) {
        is F8eError.SpecificClientError -> {
          title = when (f8eError.errorCode) {
            INVITATION_EXPIRED -> "This code has expired"
            else -> title
          }

          val subline =
            when (f8eError.errorCode) {
              ACCOUNT_ALREADY_TRUSTED_CONTACT -> {
                return ErrorFormBodyModel(
                  title = "You’re already protecting this person",
                  subline = "You’ve already accepted an invite to be a $subject for the Bitkey owner who sent this invite, and can’t accept a second time using this Bitkey account.",
                  primaryButton = ButtonDataModel(text = "Back", onClick = onBack),
                  eventTrackerScreenId = TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE,
                  errorData = ErrorData(
                    segment = segment,
                    cause = error.error.error,
                    actionDescription = ACTION_DESCRIPTION
                  )
                )
              }
              CUSTOMER_IS_TRUSTED_CONTACT -> {
                // Special case for when the user is trying to enroll as a TC for themselves.
                return ErrorFormBodyModel(
                  title = "You can’t be your own $subject",
                  subline =
                    when (devicePlatform) {
                      DevicePlatform.Jvm,
                      DevicePlatform.Android,
                      -> "Due to security reasons, another Google Drive and device needs to be used to be a $subject for this wallet."
                      DevicePlatform.IOS -> "Due to security reasons, another iCloud and device needs to be used to be a $subject for this wallet."
                    },
                  primaryButton = ButtonDataModel(text = "Back", onClick = onBack),
                  eventTrackerScreenId = TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE,
                  errorData = ErrorData(
                    segment = segment,
                    cause = error.error.error,
                    actionDescription = ACTION_DESCRIPTION
                  )
                )
              }
              MAX_PROTECTED_CUSTOMERS_REACHED -> {
                return maxProtectedCustomersReachedErrorModel(
                  isInheritance = isInheritance,
                  currentRelationships = currentRelationships,
                  segment = segment,
                  onBack = onBack,
                  error = error
                )
              }
              CODE_MISMATCH -> "The provided code was incorrect. Please try again."
              INVITATION_EXPIRED -> "The invite code you entered has expired. Reach out to your contact to request a new code."
              RELATIONSHIP_ALREADY_ESTABLISHED -> "The enrollment for this code has already been completed by someone else."
              CONFLICT -> "Account is already a trusted contact for the customer."
            }

          val buttonText = when (f8eError.errorCode) {
            INVITATION_EXPIRED -> "Got it"
            else -> "Back"
          }

          ErrorFormBodyModel(
            title = title,
            subline = subline,
            primaryButton = ButtonDataModel(text = buttonText, onClick = onBack),
            eventTrackerScreenId = eventTrackerScreenId,
            errorData = ErrorData(
              segment = segment,
              cause = error.error.error,
              actionDescription = ACTION_DESCRIPTION
            )
          )
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
              actionDescription = ACTION_DESCRIPTION
            ),
            eventTrackerScreenId = TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE
          )
        }
      }
    }
  }
}

private fun maxProtectedCustomersReachedErrorModel(
  isInheritance: Boolean,
  currentRelationships: Relationships,
  segment: AppSegment,
  onBack: () -> Unit,
  error: AcceptInvitationCodeError.F8ePropagatedError,
): BodyModel {
  val sublineText = run {
    val protectedCustomers = currentRelationships.protectedCustomers
    val hasBeneficiary = protectedCustomers.any {
      it.roles.contains(TrustedContactRole.Beneficiary)
    }
    val hasSocialRecovery = protectedCustomers.any {
      it.roles.contains(TrustedContactRole.SocialRecoveryContact)
    }

    when {
      protectedCustomers.isEmpty() -> {
        if (isInheritance) {
          "You’re already a Beneficiary for 20 people. To accept this invite, remove a Benefactor first."
        } else {
          "You’re already a Recovery Contact for 20 people. To accept this invite, remove yourself as a Recovery Contact for someone first."
        }
      }
      hasBeneficiary && hasSocialRecovery ->
        "You’re already a Recovery Contact and/or Beneficiary for 20 people. To accept this invite, remove yourself as a Recovery Contact or remove a Benefactor first."
      hasBeneficiary ->
        "You’re already a Beneficiary for 20 people. To accept this invite, remove a Benefactor first."
      hasSocialRecovery ->
        "You’re already a Recovery Contact for 20 people. To accept this invite, remove yourself as a Recovery Contact for someone first."
      else ->
        "You’ve reached the maximum number of people you can protect. To accept this invitation, you'll need to remove one of your existing relationships first."
    }
  }

  return ErrorFormBodyModel(
    title = "Maximum limit reached",
    subline = sublineText,
    primaryButton = ButtonDataModel(text = "Got it", onClick = onBack),
    eventTrackerScreenId = TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE,
    errorData = ErrorData(
      segment = segment,
      cause = error.error.error,
      actionDescription = ACTION_DESCRIPTION
    )
  )
}
