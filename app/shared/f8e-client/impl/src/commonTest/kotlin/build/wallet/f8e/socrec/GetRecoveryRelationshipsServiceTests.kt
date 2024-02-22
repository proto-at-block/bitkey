package build.wallet.f8e.socrec

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.f8e.socrec.models.CreateTrustedContactInvitation
import build.wallet.f8e.socrec.models.GetRecoveryRelationshipsResponseBody
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

class GetRecoveryRelationshipsServiceTests : FunSpec({
  test("Get Recovery Relationships - Response Deserialization") {
    val response =
      """
      {
          "invitations": [
              {
                  "recovery_relationship_id": "invitation-recovery-relationship-id",
                  "trusted_contact_alias": "invitation-trusted-contact-alias",
                  "code": "invitation-code",
                  "expires_at": "1970-01-01T00:02:03Z"
              }
          ],
          "trusted_contacts": [
              {
                  "recovery_relationship_id": "trusted-contact-recovery-relationship-id",
                  "trusted_contact_identity_pubkey": "trusted-contact-pubkey",
                  "trusted_contact_alias": "trusted-contact-alias"
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

    result.shouldBeEqual(
      GetRecoveryRelationshipsResponseBody(
        invitations =
          listOf(
            CreateTrustedContactInvitation(
              recoveryRelationshipId = "invitation-recovery-relationship-id",
              trustedContactAlias = TrustedContactAlias("invitation-trusted-contact-alias"),
              token = "invitation-code",
              expiresAt = Instant.fromEpochSeconds(123)
            )
          ),
        trustedContacts =
          listOf(
            TrustedContact(
              recoveryRelationshipId = "trusted-contact-recovery-relationship-id",
              identityKey =
                TrustedContactIdentityKey(
                  AppKey.fromPublicKey("trusted-contact-pubkey")
                ),
              trustedContactAlias = TrustedContactAlias("trusted-contact-alias")
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
