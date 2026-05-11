package build.wallet.ui.app.recovery

import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.relationships.*
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.VERIFIED
import build.wallet.compose.collections.immutableListOf
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.input.NameInputBodyModel
import build.wallet.statemachine.recovery.socrec.add.SaveContactBodyModel
import build.wallet.statemachine.recovery.socrec.add.ShareInviteBodyModel
import build.wallet.statemachine.recovery.socrec.add.TosInfo
import build.wallet.statemachine.recovery.socrec.list.full.TrustedContactsListBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class RecoveryContactEnrollmentFlowSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Recovery Contact Enrollment list initial") {
    paparazzi.snapshot {
      FormScreen(
        TrustedContactsListBodyModel(
          contacts = listOf(sampleEndorsedTrustedContact(alias = "Bob", relationshipId = "bob-id")),
          invitations = listOf(),
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

  test("Recovery Contact Enrollment enter name") {
    paparazzi.snapshot {
      FormScreen(
        NameInputBodyModel(
          title = "Add your Recovery Contact's name",
          subline = "Add a name, or nickname, to help you recognize your Recovery Contact in the app.",
          value = "Ryan",
          primaryButton = ButtonModel(
            text = "Continue",
            isEnabled = true,
            onClick = StandardClick { },
            size = ButtonModel.Size.Footer
          ),
          onValueChange = { },
          onClose = { },
          id = null,
          hasPreviousScreen = false
        )
      )
    }
  }

  test("Recovery Contact Enrollment save with Bitkey") {
    paparazzi.snapshot {
      FormScreen(
        SaveContactBodyModel(
          trustedContactName = "Ryan",
          isBeneficiary = false,
          onSave = {},
          onBackPressed = {},
          tosInfo = null
        )
      )
    }
  }

  test("Recovery Contact Enrollment save beneficiary with tos agreed and design system v2 feature flag on") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      FormScreen(
        SaveContactBodyModel(
          trustedContactName = "Ryan",
          isBeneficiary = true,
          onSave = {},
          onBackPressed = {},
          tosInfo = TosInfo(
            termsAgree = true,
            onTermsAgreeToggle = {},
            tosLink = {}
          )
        )
      )
    }
  }

  test("Recovery Contact Enrollment share invite") {
    paparazzi.snapshot {
      FormScreen(
        ShareInviteBodyModel(
          trustedContactName = "Ryan",
          isBeneficiary = false,
          onShareComplete = {},
          onBackPressed = {}
        )
      )
    }
  }

  test("Recovery Contact Enrollment list pending invite") {
    paparazzi.snapshot {
      FormScreen(
        TrustedContactsListBodyModel(
          contacts = listOf(sampleEndorsedTrustedContact(alias = "Bob", relationshipId = "bob-id")),
          invitations = listOf(
            Invitation(
              "ryan-invite-id",
              TrustedContactAlias("Ryan"),
              setOf(TrustedContactRole.SocialRecoveryContact),
              "invite-code",
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
})

private fun sampleEndorsedTrustedContact(
  alias: String,
  relationshipId: String,
): EndorsedTrustedContact {
  return EndorsedTrustedContact(
    relationshipId = relationshipId,
    trustedContactAlias = TrustedContactAlias(alias = alias),
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
}
