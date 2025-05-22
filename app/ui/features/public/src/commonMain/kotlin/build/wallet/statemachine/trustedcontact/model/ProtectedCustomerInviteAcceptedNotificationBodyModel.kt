package build.wallet.statemachine.trustedcontact.model

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.PROTECTED_CUSTOMER_INVITE_ACCEPTED
import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel

fun ProtectedCustomerInviteAcceptedNotificationBodyModel(
  trustedContact: TrustedContact,
  onDone: () -> Unit,
): FormBodyModel {
  return SuccessBodyModel(
    id = PROTECTED_CUSTOMER_INVITE_ACCEPTED,
    title = "Your Recovery Contact is now active\n",
    message =
      "${trustedContact.recoveryAlias} is now set up as a Recovery Contact of your Bitkey wallet. " +
        "You can manage your Recovery Contacts anytime in Settings.",
    primaryButtonModel = ButtonDataModel("Got it", onClick = onDone)
  )
}
