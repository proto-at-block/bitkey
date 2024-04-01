package build.wallet.f8e.socrec

import build.wallet.crypto.PublicKey
import build.wallet.f8e.socrec.models.RetrieveTrustedContactInvitation
import build.wallet.f8e.socrec.models.RetrieveTrustedContactInvitationResponseBody
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

class RetrieveTrustedContactInvitationServiceTests : FunSpec({
  test("Retrieve TC Invite - Response Deserialization") {
    val response =
      """
      {
          "invitation": {
              "recovery_relationship_id": "test-id",
              "expires_at":"1970-01-01T00:02:03Z",
              "protected_customer_enrollment_pake_pubkey": "deadbeef"
          }
      }
      """.trimIndent()

    val result: RetrieveTrustedContactInvitationResponseBody = Json.decodeFromString(response)

    result.shouldBeEqual(
      RetrieveTrustedContactInvitationResponseBody(
        invitation =
          RetrieveTrustedContactInvitation(
            recoveryRelationshipId = "test-id",
            expiresAt = Instant.fromEpochSeconds(123),
            protectedCustomerEnrollmentPakePubkey = PublicKey("deadbeef")
          )
      )
    )
  }
})
