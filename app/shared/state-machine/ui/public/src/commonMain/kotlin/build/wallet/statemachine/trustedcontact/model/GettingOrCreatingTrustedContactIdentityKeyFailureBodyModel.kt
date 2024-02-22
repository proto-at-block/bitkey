package build.wallet.statemachine.trustedcontact.model

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_TRUSTED_CONTACT_IDENTITY_KEY_FAILURE
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel

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
    eventTrackerScreenId = TC_TRUSTED_CONTACT_IDENTITY_KEY_FAILURE
  )
