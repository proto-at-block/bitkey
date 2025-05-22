package build.wallet.ui.app.recovery

import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.relationships.*
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.VERIFIED
import build.wallet.compose.collections.immutableListOf
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.recovery.socrec.list.full.TrustedContactsListBodyModel
import build.wallet.statemachine.recovery.socrec.list.lite.LiteTrustedContactsListBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class TrustedContactsListFormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Recovery Contacts list") {
    paparazzi.snapshot {
      FormScreen(
        TrustedContactsListBodyModel(
          contacts =
            listOf(
              EndorsedTrustedContact(
                "",
                trustedContactAlias = TrustedContactAlias(alias = "Bob"),
                keyCertificate = TrustedContactKeyCertificate(
                  delegatedDecryptionKey = PublicKey(""),
                  appGlobalAuthPublicKey = PublicKey(""),
                  hwAuthPublicKey = HwAuthPublicKey(Secp256k1PublicKey("")),
                  appAuthGlobalKeyHwSignature = AppGlobalAuthKeyHwSignature(""),
                  trustedContactIdentityKeyAppSignature = TcIdentityKeyAppSignature("")
                ),
                authenticationState = VERIFIED,
                roles = setOf(TrustedContactRole.SocialRecoveryContact)
              )
            ),
          invitations =
            listOf(
              Invitation(
                "",
                TrustedContactAlias("Alice"),
                setOf(TrustedContactRole.SocialRecoveryContact),
                "",
                20,
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

  test("Recovery Contacts list lite") {
    paparazzi.snapshot {
      FormScreen(
        LiteTrustedContactsListBodyModel(
          protectedCustomers =
            immutableListOf(
              ProtectedCustomer(
                relationshipId = "",
                alias = ProtectedCustomerAlias("Alice"),
                roles = setOf(TrustedContactRole.SocialRecoveryContact)
              )
            ),
          onProtectedCustomerPressed = {},
          onAcceptInvitePressed = {},
          onBackPressed = {}
        )
      )
    }
  }
})
