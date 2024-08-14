package build.wallet.statemachine.trustedcontact.model

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_SUCCESS
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel

fun AcceptingInviteWithF8eSuccessBodyModel(
  protectedCustomer: ProtectedCustomer,
  onDone: () -> Unit,
): FormBodyModel =
  SuccessBodyModel(
    id = TC_ENROLLMENT_SUCCESS,
    title = "You're all set.",
    message = "We’ve securely saved a recovery key for ${protectedCustomer.alias.alias}'s wallet to this cloud account",
    primaryButtonModel = ButtonDataModel("Got it", onClick = onDone)
  )
