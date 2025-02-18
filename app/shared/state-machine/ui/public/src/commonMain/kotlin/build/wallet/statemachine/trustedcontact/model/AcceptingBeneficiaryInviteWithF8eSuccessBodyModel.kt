package build.wallet.statemachine.trustedcontact.model

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_BENEFICIARY_ENROLLMENT_INVITE_ACCEPTED
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_INVITE_ACCEPTED
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel

fun AcceptingInviteWithF8eSuccessBodyModel(
  recoveryRelationshipRoles: Set<TrustedContactRole>,
  protectedCustomer: ProtectedCustomer,
  onDone: () -> Unit,
): FormBodyModel =
  SuccessBodyModel(
    id = if (recoveryRelationshipRoles.singleOrNull() == TrustedContactRole.Beneficiary) {
      TC_BENEFICIARY_ENROLLMENT_INVITE_ACCEPTED
    } else {
      TC_ENROLLMENT_INVITE_ACCEPTED
    },
    title = "You're all set",
    message = if (protectedCustomer.roles.contains(TrustedContactRole.Beneficiary)) {
      "You are now set up as a beneficiary of ${protectedCustomer.alias.alias}'s wallet. You can manage inheritance claims by going to Inheritance in Settings."
    } else {
      "We’ve securely saved a recovery key for ${protectedCustomer.alias.alias}'s wallet to this cloud account"
    },
    primaryButtonModel = if (protectedCustomer.roles.contains(TrustedContactRole.Beneficiary)) {
      ButtonDataModel("Done", onClick = onDone)
    } else {
      ButtonDataModel("Got it", onClick = onDone)
    }
  )
