package build.wallet.f8e.socrec

import build.wallet.bitkey.relationships.*
import build.wallet.bitkey.socrec.TrustedContactKeyCertificateFake
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.socrec.models.CreateTrustedContactInvitation
import build.wallet.f8e.socrec.models.GetRecoveryRelationshipsResponseBody
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.decodeHex

class GetRecoveryRelationshipsF8eClientTests : FunSpec({
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
                  "expires_at": "1970-01-01T00:02:03Z",
                  "trusted_contact_roles": ["SOCIAL_RECOVERY_CONTACT"]
              }
          ],
          "unendorsed_trusted_contacts": [
              {
                  "recovery_relationship_id": "unendorsed-trusted-contact-recovery-relationship-id",
                  "trusted_contact_alias": "trusted-contact-alias",
                  "sealed_delegated_decryption_pubkey": "sealed-delegated-decryption-pubkey",
                  "trusted_contact_enrollment_pake_pubkey": "enrollment-pake-pubkey",
                  "enrollment_pake_confirmation": "deadbeef",
                  "trusted_contact_roles": ["SOCIAL_RECOVERY_CONTACT"]
              }
          ],
          "endorsed_trusted_contacts": [
              {
                  "recovery_relationship_id": "trusted-contact-recovery-relationship-id",
                  "trusted_contact_alias": "trusted-contact-alias",
                  "delegated_decryption_pubkey_certificate": "eyJkZWxlZ2F0ZWRfZGVjcnlwdGlvbl9rZXkiOiJkZWFkYmVlZiIsImh3X2VuZG9yc2VtZW50X2tleSI6eyJwdWJLZXkiOiJody1hdXRoLWRwdWIifSwiYXBwX2VuZG9yc2VtZW50X2tleSI6eyJwdWJLZXkiOiJhcHAtYXV0aC1kcHViIn0sImh3X3NpZ25hdHVyZSI6ImFwcC1nbG9iYWwtYXV0aC1rZXktaHctc2lnbmF0dXJlIiwiYXBwX3NpZ25hdHVyZSI6InRjLWlkZW50aXR5LWtleS1hcHAtc2lnbmF0dXJlLW1vY2sifQ==",
                  "trusted_contact_roles": ["SOCIAL_RECOVERY_CONTACT"]
              }
          ],
          "customers": [
              {
                  "recovery_relationship_id": "customer-recovery-relationship-id",
                  "customer_alias": "customer-alias",
                  "trusted_contact_roles": ["SOCIAL_RECOVERY_CONTACT"]
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
              relationshipId = "invitation-recovery-relationship-id",
              trustedContactAlias = TrustedContactAlias("invitation-trusted-contact-alias"),
              code = "F00D",
              codeBitLength = 20,
              expiresAt = Instant.fromEpochSeconds(123),
              roles = setOf(TrustedContactRole.SocialRecoveryContact)
            )
          ),
        unendorsedTrustedContacts =
          listOf(
            UnendorsedTrustedContact(
              relationshipId = "unendorsed-trusted-contact-recovery-relationship-id",
              sealedDelegatedDecryptionKey = XCiphertext("sealed-delegated-decryption-pubkey"),
              enrollmentPakeKey = PublicKey("enrollment-pake-pubkey"),
              enrollmentKeyConfirmation = "deadbeef".decodeHex(),
              trustedContactAlias = TrustedContactAlias("trusted-contact-alias"),
              roles = setOf(TrustedContactRole.SocialRecoveryContact)
            )
          ),
        endorsedEndorsedTrustedContacts =
          listOf(
            F8eEndorsedTrustedContact(
              relationshipId = "trusted-contact-recovery-relationship-id",
              trustedContactAlias = TrustedContactAlias("trusted-contact-alias"),
              keyCertificate = TrustedContactKeyCertificateFake,
              roles = setOf(TrustedContactRole.SocialRecoveryContact)
            )
          ),
        customers =
          listOf(
            ProtectedCustomer(
              relationshipId = "customer-recovery-relationship-id",
              alias = ProtectedCustomerAlias("customer-alias"),
              roles = setOf(TrustedContactRole.SocialRecoveryContact)
            )
          )
      )
    )
  }
})
