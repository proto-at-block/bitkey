package build.wallet.ui.app.recovery

import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.trustedcontact.remove.RemoveTrustedContactBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class RemoveTrustedContactScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("remove recovery contact - active") {
    paparazzi.snapshot {
      FormScreen(
        model = RemoveTrustedContactBodyModel(
          trustedContactAlias = TrustedContactAlias("Alice"),
          isExpiredInvitation = false,
          onRemove = {},
          onClosed = {},
          isBeneficiary = false
        )
      )
    }
  }

  test("remove recovery contact - expired invitation") {
    paparazzi.snapshot {
      FormScreen(
        model = RemoveTrustedContactBodyModel(
          trustedContactAlias = TrustedContactAlias("Alice"),
          isExpiredInvitation = true,
          onRemove = {},
          onClosed = {},
          isBeneficiary = false
        )
      )
    }
  }

  test("remove beneficiary - active") {
    paparazzi.snapshot {
      FormScreen(
        model = RemoveTrustedContactBodyModel(
          trustedContactAlias = TrustedContactAlias("Bob"),
          isExpiredInvitation = false,
          onRemove = {},
          onClosed = {},
          isBeneficiary = true
        )
      )
    }
  }

  test("remove beneficiary - expired invitation") {
    paparazzi.snapshot {
      FormScreen(
        model = RemoveTrustedContactBodyModel(
          trustedContactAlias = TrustedContactAlias("Bob"),
          isExpiredInvitation = true,
          onRemove = {},
          onClosed = {},
          isBeneficiary = true
        )
      )
    }
  }
})
