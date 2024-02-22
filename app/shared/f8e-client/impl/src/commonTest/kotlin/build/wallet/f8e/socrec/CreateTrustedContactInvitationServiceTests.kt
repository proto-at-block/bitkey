package build.wallet.f8e.socrec

import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.f8e.socrec.models.CreateTrustedContactInvitation
import build.wallet.f8e.socrec.models.CreateTrustedContactInvitationRequestBody
import build.wallet.f8e.socrec.models.CreateTrustedContactInvitationResponseBody
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CreateTrustedContactInvitationServiceTests : FunSpec({
  test("Create TC Invite - Request Serialization") {
    val request =
      CreateTrustedContactInvitationRequestBody(
        trustedContactAlias = TrustedContactAlias("test")
      )
    val result = Json.encodeToString(request)

    result.shouldBeEqual("""{"trusted_contact_alias":"test"}""")
  }

  test("Create TC Invite - Response Deserialization") {
    val response =
      """
      {
          "invitation": {
              "recovery_relationship_id": "test-id",
              "trusted_contact_alias":"test-tc-alias",
              "code":"test-token",
              "expires_at":"1970-01-01T00:02:03Z"
          }
      }
      """.trimIndent()

    val result =
      Json.decodeFromString<CreateTrustedContactInvitationResponseBody>(
        response
      )

    result.shouldBeEqual(
      CreateTrustedContactInvitationResponseBody(
        invitation =
          CreateTrustedContactInvitation(
            recoveryRelationshipId = "test-id",
            trustedContactAlias = TrustedContactAlias("test-tc-alias"),
            token = "test-token",
            expiresAt = Instant.fromEpochSeconds(123)
          )
      )
    )
  }
})
