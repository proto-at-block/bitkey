package build.wallet.statemachine.trustedcontact.model

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.BENEFACTOR_INVITE_ACCEPTED
import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel

fun BenefactorInviteAcceptedNotificationBodyModel(
  beneficiary: TrustedContact,
  onDone: () -> Unit,
): FormBodyModel {
  return SuccessBodyModel(
    id = BENEFACTOR_INVITE_ACCEPTED,
    title = "Your plan is now active",
    message =
      "${beneficiary.recoveryAlias} is now set up as a beneficiary of your Bitkey wallet. You can " +
        "manage beneficiaries by going to Inheritance in Settings.\n\n" +
        "Reminder: Always consult an estate planning professional for additional local and federal requirements.",
    primaryButtonModel = ButtonDataModel("Got it", onClick = onDone)
  )
}
