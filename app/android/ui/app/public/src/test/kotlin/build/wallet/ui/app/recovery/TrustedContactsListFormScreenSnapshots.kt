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
        trustedContactsListBodyModel(
          contacts =
            listOf(
              sampleEndorsedTrustedContact(
                alias = "Bob",
                relationshipId = "bob-contact-id"
              )
            ),
          invitations =
            listOf(
              sampleInvitation(
                alias = "Alice",
                relationshipId = "alice-invite-id"
              )
            ),
          protectedCustomers =
            immutableListOf(
              sampleProtectedCustomer(
                alias = "Charlie",
                relationshipId = "charlie-protected-id"
              ),
              sampleProtectedCustomer(
                alias = "Dana",
                relationshipId = "dana-protected-id"
              )
            )
        )
      )
    }
  }

  test("Recovery Contacts list empty") {
    paparazzi.snapshot {
      FormScreen(
        trustedContactsListBodyModel()
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

  test("Recovery Contacts list with design system v2") {
    paparazzi.snapshot {
      FormScreen(
        trustedContactsListBodyModel(
          contacts =
            listOf(
              sampleEndorsedTrustedContact(
                alias = "Bob",
                relationshipId = "bob-contact-id"
              )
            ),
          invitations =
            listOf(
              sampleInvitation(
                alias = "Alice",
                relationshipId = "alice-invite-id"
              )
            ),
          protectedCustomers =
            immutableListOf(
              sampleProtectedCustomer(
                alias = "Charlie",
                relationshipId = "charlie-protected-id"
              ),
              sampleProtectedCustomer(
                alias = "Dana",
                relationshipId = "dana-protected-id"
              )
            )
        )
      )
    }
  }

  test("Recovery Contacts list empty with design system v2") {
    paparazzi.snapshot {
      FormScreen(
        trustedContactsListBodyModel()
      )
    }
  }
})

private fun trustedContactsListBodyModel(
  contacts: List<EndorsedTrustedContact> = emptyList(),
  invitations: List<Invitation> = emptyList(),
  protectedCustomers: List<ProtectedCustomer> = emptyList(),
): TrustedContactsListBodyModel {
  return TrustedContactsListBodyModel(
    contacts = contacts,
    invitations = invitations,
    protectedCustomers = protectedCustomers,
    now = Clock.System.now().toEpochMilliseconds(),
    onAddPressed = {},
    onContactPressed = {},
    onProtectedCustomerPressed = {},
    onAcceptInvitePressed = {},
    onBackPressed = {}
  )
}

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

private fun sampleInvitation(
  alias: String,
  relationshipId: String,
): Invitation {
  return Invitation(
    relationshipId = relationshipId,
    trustedContactAlias = TrustedContactAlias(alias),
    roles = setOf(TrustedContactRole.SocialRecoveryContact),
    code = "$relationshipId-code",
    codeBitLength = 20,
    expiresAt = Instant.DISTANT_FUTURE
  )
}

private fun sampleProtectedCustomer(
  alias: String,
  relationshipId: String,
): ProtectedCustomer {
  return ProtectedCustomer(
    relationshipId = relationshipId,
    alias = ProtectedCustomerAlias(alias),
    roles = setOf(TrustedContactRole.SocialRecoveryContact)
  )
}
