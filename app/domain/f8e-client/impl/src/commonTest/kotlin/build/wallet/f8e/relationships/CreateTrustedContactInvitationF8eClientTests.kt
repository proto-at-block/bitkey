package build.wallet.f8e.relationships

import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.crypto.PublicKey
import build.wallet.f8e.relationships.models.CreateRelationshipInvitationRequestBody
import build.wallet.f8e.relationships.models.CreateRelationshipInvitationResponseBody
import build.wallet.f8e.relationships.models.RelationshipInvitation
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CreateTrustedContactInvitationF8eClientTests : FunSpec({
  test("Create TC Invite - Request Serialization") {
    val request =
      CreateRelationshipInvitationRequestBody(
        trustedContactAlias = TrustedContactAlias("test"),
        protectedCustomerEnrollmentPakeKey = PublicKey("key"),
        roles = listOf(TrustedContactRole.SocialRecoveryContact)
      )
    val result = Json.encodeToString(request)

    result.shouldEqualJson(
      """
      {
        "trusted_contact_alias":"test",
        "protected_customer_enrollment_pake_pubkey": "key",
        "trusted_contact_roles": ["SOCIAL_RECOVERY_CONTACT"]
      }
      """
    )
  }

  test("Create TC Invite - Response Deserialization") {
    val response =
      """
      {
          "invitation": {
              "recovery_relationship_id": "test-id",
              "trusted_contact_alias":"test-tc-alias",
              "code":"F00D",
              "code_bit_length":20,
              "expires_at":"1970-01-01T00:02:03Z",
              "trusted_contact_roles": ["SOCIAL_RECOVERY_CONTACT"]
          }
      }
      """.trimIndent()

    val result =
      Json.decodeFromString<CreateRelationshipInvitationResponseBody>(
        response
      )

    result.shouldBeEqual(
      CreateRelationshipInvitationResponseBody(
        invitation =
          RelationshipInvitation(
            relationshipId = "test-id",
            trustedContactAlias = TrustedContactAlias("test-tc-alias"),
            code = "F00D",
            codeBitLength = 20,
            expiresAt = Instant.fromEpochSeconds(123),
            roles = setOf(TrustedContactRole.SocialRecoveryContact)
          )
      )
    )
  }
})
