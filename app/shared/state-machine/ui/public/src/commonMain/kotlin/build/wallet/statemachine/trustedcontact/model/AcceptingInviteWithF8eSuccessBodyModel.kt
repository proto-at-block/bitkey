package build.wallet.statemachine.trustedcontact.model

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_SUCCESS
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.SuccessBodyModel

fun AcceptingInviteWithF8eSuccessBodyModel(
  protectedCustomer: ProtectedCustomer,
  onDone: () -> Unit,
): SuccessBodyModel =
  SuccessBodyModel(
    id = TC_ENROLLMENT_SUCCESS,
    title = "That's it!",
    message = "We’ve securely saved a recovery key for ${protectedCustomer.alias.alias}'s wallet to this cloud account",
    style =
      SuccessBodyModel.Style.Explicit(
        primaryButton =
          ButtonDataModel(
            text = "Got it",
            onClick = onDone
          )
      )
  )
