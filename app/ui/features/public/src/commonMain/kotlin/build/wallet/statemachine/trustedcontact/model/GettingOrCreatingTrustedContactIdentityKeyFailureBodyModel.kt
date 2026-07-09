package build.wallet.statemachine.trustedcontact.model

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_DELEGATED_DECRYPTION_KEY_KEY_FAILURE
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.recovery.RecoverySegment

fun GettingOrCreatingTrustedContactIdentityKeyFailureBodyModel(onClick: () -> Unit) =
  ErrorFormBodyModel(
    title = "Error preparing account",
    subline = "Please try again later.",
    primaryButton =
      ButtonDataModel(
        text = "Ok",
        onClick = onClick
      ),
    onBack = onClick,
    errorData = ErrorData(
      segment = RecoverySegment.SocRec.TrustedContact.Setup,
      actionDescription = "Getting or creating trusted contact identity key",
      cause = IllegalStateException("Failed to get or create trusted contact identity key")
    ),
    eventTrackerScreenId = TC_DELEGATED_DECRYPTION_KEY_KEY_FAILURE
  )
