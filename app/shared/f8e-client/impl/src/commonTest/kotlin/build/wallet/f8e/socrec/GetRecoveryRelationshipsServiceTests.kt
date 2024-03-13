package build.wallet.f8e.socrec

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState.AWAITING_VERIFY
import build.wallet.bitkey.socrec.TrustedContactEnrollmentPakeKey
import build.wallet.bitkey.socrec.TrustedContactKeyCertificateFake
import build.wallet.bitkey.socrec.UnendorsedTrustedContact
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.socrec.models.CreateTrustedContactInvitation
import build.wallet.f8e.socrec.models.GetRecoveryRelationshipsResponseBody
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.decodeHex

class GetRecoveryRelationshipsServiceTests : FunSpec({
  test("Get Recovery Relationships - Response Deserialization") {
    val response =
      """
      {
          "invitations": [
              {
                  "recovery_relationship_id": "invitation-recovery-relationship-id",
                  "trusted_contact_alias": "invitation-trusted-contact-alias",
                  "code": "F00D",
                  "code_bit_length": 20,
                  "expires_at": "1970-01-01T00:02:03Z"
              }
          ],
          "unendorsed_trusted_contacts": [
              {
                  "recovery_relationship_id": "unendorsed-trusted-contact-recovery-relationship-id",
                  "trusted_contact_alias": "trusted-contact-alias",
                  "sealed_delegated_decryption_pubkey": "sealed-delegated-decryption-pubkey",
                  "trusted_contact_enrollment_pake_pubkey": "enrollment-pake-pubkey",
                  "enrollment_pake_confirmation": "deadbeef"
              }
          ],
          "endorsed_trusted_contacts": [
              {
                  "recovery_relationship_id": "trusted-contact-recovery-relationship-id",
                  "trusted_contact_alias": "trusted-contact-alias",
                  "delegated_decryption_pubkey_certificate": "eyJkZWxlZ2F0ZWRfZGVjcnlwdGlvbl9rZXkiOiJkZWFkYmVlZiIsImh3X2VuZG9yc2VtZW50X2tleSI6eyJwdWJLZXkiOiJody1hdXRoLWRwdWIifSwiYXBwX2VuZG9yc2VtZW50X2tleSI6eyJwdWJLZXkiOiJhcHAtYXV0aC1kcHViIn0sImh3X3NpZ25hdHVyZSI6ImFwcC1nbG9iYWwtYXV0aC1rZXktaHctc2lnbmF0dXJlIiwiYXBwX3NpZ25hdHVyZSI6InRjLWlkZW50aXR5LWtleS1hcHAtc2lnbmF0dXJlLW1vY2sifQ=="
              }
          ],
          "customers": [
              {
                  "recovery_relationship_id": "customer-recovery-relationship-id",
                  "customer_alias": "customer-alias"
              }
          ]
      }        
      """.trimIndent()

    val result =
      Json.decodeFromString<GetRecoveryRelationshipsResponseBody>(
        response
      )

    result.shouldBe(
      GetRecoveryRelationshipsResponseBody(
        invitations =
          listOf(
            CreateTrustedContactInvitation(
              recoveryRelationshipId = "invitation-recovery-relationship-id",
              trustedContactAlias = TrustedContactAlias("invitation-trusted-contact-alias"),
              code = "F00D",
              codeBitLength = 20,
              expiresAt = Instant.fromEpochSeconds(123)
            )
          ),
        unendorsedTrustedContacts =
          listOf(
            UnendorsedTrustedContact(
              recoveryRelationshipId = "unendorsed-trusted-contact-recovery-relationship-id",
              sealedDelegatedDecryptionKey = XCiphertext("sealed-delegated-decryption-pubkey"),
              enrollmentPakeKey = TrustedContactEnrollmentPakeKey(
                AppKey.fromPublicKey("enrollment-pake-pubkey")
              ),
              enrollmentKeyConfirmation = "deadbeef".decodeHex(),
              trustedContactAlias = TrustedContactAlias("trusted-contact-alias")
            )
          ),
        endorsedTrustedContacts =
          listOf(
            TrustedContact(
              recoveryRelationshipId = "trusted-contact-recovery-relationship-id",
              trustedContactAlias = TrustedContactAlias("trusted-contact-alias"),
              // Default auth state is AWAITING_VERIFY, until the app verifies the TC.
              authenticationState = AWAITING_VERIFY,
              keyCertificate = TrustedContactKeyCertificateFake
            )
          ),
        customers =
          listOf(
            ProtectedCustomer(
              recoveryRelationshipId = "customer-recovery-relationship-id",
              alias = ProtectedCustomerAlias("customer-alias")
            )
          )
      )
    )
  }
})
