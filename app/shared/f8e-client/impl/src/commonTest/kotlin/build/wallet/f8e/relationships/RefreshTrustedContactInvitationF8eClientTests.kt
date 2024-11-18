package build.wallet.f8e.relationships

import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.f8e.relationships.models.RefreshTrustedContactRequestBody
import build.wallet.f8e.relationships.models.RefreshTrustedContactResponseBody
import build.wallet.f8e.relationships.models.RelationshipInvitation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RefreshTrustedContactInvitationF8eClientTests : FunSpec({
  test("Refresh TC Invite - Request Serialization") {
    val result = Json.encodeToString(RefreshTrustedContactRequestBody(action = "Reissue"))

    result.shouldBeEqual("""{"action":"Reissue"}""")
  }

  test("Refresh TC Invite - Response Deserialization") {
    val response =
      """
      {
        "invitation": {
          "recovery_relationship_id": "123",
          "trusted_contact_alias": "test-alias",
          "code": "F00D",
          "code_bit_length": 20,
          "expires_at": "1970-01-01T00:02:03Z",
          "trusted_contact_roles": ["SOCIAL_RECOVERY_CONTACT"]
        }
      }
      """.trimIndent()

    val result = Json.decodeFromString<RefreshTrustedContactResponseBody>(response)

    result.shouldBeEqual(
      RefreshTrustedContactResponseBody(
        invitation =
          RelationshipInvitation(
            relationshipId = "123",
            trustedContactAlias = TrustedContactAlias("test-alias"),
            code = "F00D",
            codeBitLength = 20,
            expiresAt = Instant.fromEpochSeconds(123),
            roles = setOf(TrustedContactRole.SocialRecoveryContact)
          )
      )
    )
  }
})
