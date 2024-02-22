package build.wallet.ui.app.recovery

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.compose.collections.immutableListOf
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.recovery.socrec.list.full.TrustedContactsListBodyModel
import build.wallet.statemachine.recovery.socrec.list.lite.LiteTrustedContactsListBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class TrustedContactsListFormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("trusted contacts list") {
    paparazzi.snapshot {
      FormScreen(
        TrustedContactsListBodyModel(
          contacts =
            listOf(
              TrustedContact(
                "",
                TrustedContactAlias("Bob"),
                TrustedContactIdentityKey(AppKey.fromPublicKey(""))
              )
            ),
          invitations =
            listOf(
              Invitation(
                "",
                TrustedContactAlias("Alice"),
                "",
                Instant.DISTANT_FUTURE
              )
            ),
          protectedCustomers = immutableListOf(),
          now = Clock.System.now().toEpochMilliseconds(),
          onAddPressed = {},
          onContactPressed = {},
          onProtectedCustomerPressed = {},
          onAcceptInvitePressed = {},
          onBackPressed = {}
        )
      )
    }
  }

  test("trusted contacts list lite") {
    paparazzi.snapshot {
      FormScreen(
        LiteTrustedContactsListBodyModel(
          protectedCustomers =
            immutableListOf(
              ProtectedCustomer("", ProtectedCustomerAlias("Alice"))
            ),
          onProtectedCustomerPressed = {},
          onAcceptInvitePressed = {},
          onBackPressed = {}
        )
      )
    }
  }
})
