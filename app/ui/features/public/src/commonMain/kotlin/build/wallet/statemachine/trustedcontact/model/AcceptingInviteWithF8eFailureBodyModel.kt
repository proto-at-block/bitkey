package build.wallet.statemachine.trustedcontact.model

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.AcceptTrustedContactInvitationErrorCode.*
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE
import build.wallet.platform.device.DevicePlatform
import build.wallet.relationships.AcceptInvitationCodeError
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.NetworkErrorFormBodyModel

fun AcceptingInviteWithF8eFailureBodyModel(
  isInheritance: Boolean,
  onBack: () -> Unit,
  onRetry: () -> Unit,
  devicePlatform: DevicePlatform,
  error: AcceptInvitationCodeError,
): BodyModel {
  val subject = if (isInheritance) "beneficiary" else "Trusted Contact"
  val title = "We couldn’t complete your enrollment as a $subject"
  val eventTrackerScreenId = TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE

  return when (error) {
    is AcceptInvitationCodeError.InvalidInvitationCode,
    is AcceptInvitationCodeError.CryptoError,
    ->
      ErrorFormBodyModel(
        title = title,
        subline = "The provided code was incorrect. Please try again.",
        primaryButton = ButtonDataModel(text = "Back", onClick = onBack),
        eventTrackerScreenId = eventTrackerScreenId
      )
    is AcceptInvitationCodeError.F8ePropagatedError -> {
      when (val f8eError = error.error) {
        is F8eError.SpecificClientError -> {
          val subline =
            when (f8eError.errorCode) {
              ACCOUNT_ALREADY_TRUSTED_CONTACT -> {
                return ErrorFormBodyModel(
                  title = "You’re already protecting this person",
                  subline = "You’ve already accepted an invite to be a $subject for the Bitkey owner who sent this invite, and can’t accept a second time using this Bitkey account.",
                  primaryButton = ButtonDataModel(text = "Back", onClick = onBack),
                  eventTrackerScreenId = TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE
                )
              }
              CUSTOMER_IS_TRUSTED_CONTACT -> {
                // Special case for when the user is trying to enroll as a TC for themselves.
                return ErrorFormBodyModel(
                  title = "You can't be your own $subject",
                  subline =
                    when (devicePlatform) {
                      DevicePlatform.Jvm,
                      DevicePlatform.Android,
                      -> "Due to security reasons, another Google Drive and device needs to be used to be a $subject for this wallet."
                      DevicePlatform.IOS -> "Due to security reasons, another iCloud and device needs to be used to be a $subject for this wallet."
                    },
                  primaryButton = ButtonDataModel(text = "Back", onClick = onBack),
                  eventTrackerScreenId = TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE
                )
              }
              INVITATION_CODE_MISMATCH -> "The provided code was incorrect. Please try again."
              INVITATION_EXPIRED -> "The provided code has expired. Please try again."
              RELATIONSHIP_ALREADY_ESTABLISHED -> "The enrollment for this code has already been completed by someone else."
            }

          ErrorFormBodyModel(
            title = title,
            subline = subline,
            primaryButton = ButtonDataModel(text = "Back", onClick = onBack),
            eventTrackerScreenId = eventTrackerScreenId
          )
        }

        else -> {
          val isConnectivityError = f8eError is F8eError.ConnectivityError
          NetworkErrorFormBodyModel(
            title = title,
            isConnectivityError = isConnectivityError,
            onRetry = onRetry.takeIf { isConnectivityError },
            onBack = onBack,
            eventTrackerScreenId = TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE
          )
        }
      }
    }
  }
}
