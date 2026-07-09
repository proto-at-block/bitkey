package build.wallet.ui.app.recovery

import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.trustedcontact.remove.RemovalContext
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
          onRemove = {},
          onClosed = {},
          isBeneficiary = false,
          removalContext = RemovalContext.ActiveRelationship
        )
      )
    }
  }

  test("remove recovery contact - expired invitation") {
    paparazzi.snapshot {
      FormScreen(
        model = RemoveTrustedContactBodyModel(
          trustedContactAlias = TrustedContactAlias("Alice"),
          onRemove = {},
          onClosed = {},
          isBeneficiary = false,
          removalContext = RemovalContext.ExpiredInvitation
        )
      )
    }
  }

  test("remove beneficiary - active") {
    paparazzi.snapshot {
      FormScreen(
        model = RemoveTrustedContactBodyModel(
          trustedContactAlias = TrustedContactAlias("Bob"),
          onRemove = {},
          onClosed = {},
          isBeneficiary = true,
          removalContext = RemovalContext.ActiveRelationship
        )
      )
    }
  }

  test("remove beneficiary - expired invitation") {
    paparazzi.snapshot {
      FormScreen(
        model = RemoveTrustedContactBodyModel(
          trustedContactAlias = TrustedContactAlias("Bob"),
          onRemove = {},
          onClosed = {},
          isBeneficiary = true,
          removalContext = RemovalContext.ExpiredInvitation
        )
      )
    }
  }
})
