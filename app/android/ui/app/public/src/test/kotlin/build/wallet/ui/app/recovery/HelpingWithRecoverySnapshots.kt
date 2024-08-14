package build.wallet.ui.app.recovery

import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.ProtectedCustomerAlias
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.recovery.socrec.help.model.ConfirmingIdentityFormBodyModel
import build.wallet.statemachine.recovery.socrec.help.model.VerifyingContactMethodFormBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class HelpingWithRecoverySnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("verifying contact method screen") {
    paparazzi.snapshot {
      FormScreen(
        model =
          VerifyingContactMethodFormBodyModel(
            onBack = {},
            onEmailClick = {},
            onPhoneCallClick = {},
            onTextMessageClick = {},
            onInPersonClick = {},
            onVideoChatClick = {}
          )
      )
    }
  }

  test("confirming identity screen") {
    paparazzi.snapshot {
      FormScreen(
        model =
          ConfirmingIdentityFormBodyModel(
            protectedCustomer =
              ProtectedCustomer(
                relationshipId = "id",
                alias = ProtectedCustomerAlias("Customer Name"),
                roles = setOf(TrustedContactRole.SocialRecoveryContact)
              ),
            onBack = {},
            onVerifiedClick = {}
          )
      )
    }
  }
})
