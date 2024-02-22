package build.wallet.f8e.socrec

import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.f8e.socrec.models.AcceptTrustedContactInvitationRequestBody
import build.wallet.f8e.socrec.models.AcceptTrustedContactInvitationResponseBody
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AcceptTrustedContactInvitationServiceTests : FunSpec({
  test("Accept TC Invite - Request Serialization") {
    val request =
      AcceptTrustedContactInvitationRequestBody(
        code = "1234",
        customerAlias = "Some Alias",
        trustedContactIdentityKey = "tc-pubkey"
      )
    val result = Json.encodeToString(request)

    result.shouldBeEqual(
      """{"action":"Accept","code":"1234","customer_alias":"Some Alias","trusted_contact_identity_pubkey":"tc-pubkey"}"""
    )
  }

  test("Accept TC Invite - Response Deserialization") {
    val response =
      """
      {
        "customer": {
          "customer_alias": "Some Alias",
          "recovery_relationship_id": "test-id"
        }
      }
      """.trimIndent()

    val result: AcceptTrustedContactInvitationResponseBody = Json.decodeFromString(response)

    result.shouldBeEqual(
      AcceptTrustedContactInvitationResponseBody(
        customer =
          ProtectedCustomer(
            alias = ProtectedCustomerAlias("Some Alias"),
            recoveryRelationshipId = "test-id"
          )
      )
    )
  }
})
