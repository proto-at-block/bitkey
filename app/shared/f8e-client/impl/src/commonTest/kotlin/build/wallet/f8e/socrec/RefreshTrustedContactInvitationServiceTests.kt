package build.wallet.f8e.socrec

import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.f8e.socrec.models.CreateTrustedContactInvitation
import build.wallet.f8e.socrec.models.RefreshTrustedContactRequestBody
import build.wallet.f8e.socrec.models.RefreshTrustedContactResponseBody
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RefreshTrustedContactInvitationServiceTests : FunSpec({
  test("Refresh TC Invite - Request Serialization") {
    val result = Json.encodeToString(RefreshTrustedContactRequestBody())

    result.shouldBeEqual("""{"action":"Reissue"}""")
  }

  test("Refresh TC Invite - Response Deserialization") {
    val response =
      """
      {
        "invitation": {
          "recovery_relationship_id": "123",
          "trusted_contact_alias": "test-alias",
          "code": "test-token",
          "expires_at": "1970-01-01T00:02:03Z"
        }
      }
      """.trimIndent()

    val result = Json.decodeFromString<RefreshTrustedContactResponseBody>(response)

    result.shouldBeEqual(
      RefreshTrustedContactResponseBody(
        invitation =
          CreateTrustedContactInvitation(
            recoveryRelationshipId = "123",
            trustedContactAlias = TrustedContactAlias("test-alias"),
            token = "test-token",
            expiresAt = Instant.fromEpochSeconds(123)
          )
      )
    )
  }
})
