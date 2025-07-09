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
  val subject = if (isInheritance) "beneficiary" else "Recovery Contact"
  var title = "We couldn’t complete your enrollment as a $subject"
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
              INVITATION_EXPIRED -> "The invite code you entered has expired. Reach out to your contact to request a new code."
              RELATIONSHIP_ALREADY_ESTABLISHED -> "The enrollment for this code has already been completed by someone else."
            }

          val buttonText = when (f8eError.errorCode) {
            INVITATION_EXPIRED -> "Got it"
            else -> "Back"
          }

          ErrorFormBodyModel(
            title = title,
            subline = subline,
            primaryButton = ButtonDataModel(text = buttonText, onClick = onBack),
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
