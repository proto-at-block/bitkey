package build.wallet.statemachine.trustedcontact.model

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.inheritance.InheritanceAppSegment
import build.wallet.statemachine.recovery.RecoverySegment

fun UploadDelegatedDecryptionKeyFailureBodyModel(
  isInheritance: Boolean,
  onBack: () -> Unit,
  onRetry: () -> Unit,
): BodyModel {
  return ErrorFormBodyModel(
    title = "We couldn’t complete your enrollment as a ${if (isInheritance) "beneficiary" else "Recovery Contact"}",
    subline = "Please try again.",
    secondaryButton = ButtonDataModel(text = "Back", onClick = onBack),
    primaryButton = ButtonDataModel(text = "Retry", onClick = onRetry),
    errorData = ErrorData(
      segment = if (isInheritance) {
        InheritanceAppSegment.BeneficiaryClaim.Start
      } else {
        RecoverySegment.SocRec.TrustedContact.Setup
      },
      actionDescription = "Uploading delegated decryption key",
      cause = IllegalStateException("Failed to upload delegated decryption key")
    ),
    eventTrackerScreenId = SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_UPLOAD_DELEGATED_DECRYPTION_KEY_FAILURE
  )
}
