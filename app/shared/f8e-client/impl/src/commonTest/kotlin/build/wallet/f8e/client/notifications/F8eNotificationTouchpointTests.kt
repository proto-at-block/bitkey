package build.wallet.f8e.client.notifications

import build.wallet.f8e.notifications.F8eNotificationTouchpoint
import build.wallet.f8e.notifications.F8eNotificationTouchpoint.F8eEmailTouchpoint
import build.wallet.f8e.notifications.F8eNotificationTouchpoint.F8ePhoneNumberTouchpoint
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.serialization.json.Json

class F8eNotificationTouchpointTests : FunSpec({

  val jsonDecoder = Json { ignoreUnknownKeys = true }

  test("phone deserialized from list") {
    val json =
      """
      [{"type":"Phone","id":"urn:wallet-touchpoint:01H6PBPJJBER6F2759B4975M0B","phone_number":"+14045551234","verified":true}]
      """.trimIndent()
    val touchpoints: List<F8eNotificationTouchpoint> = jsonDecoder.decodeFromString(json)
    touchpoints.first().shouldBeTypeOf<F8ePhoneNumberTouchpoint>()
      .phoneNumber.shouldBe("+14045551234")
  }

  test("email deserialized from list") {
    val json =
      """
      [{"type":"Email","id":"urn:wallet-touchpoint:01H6PD45SD4FR4KFPJZP34HK5T","email_address":"a@b.com","verified":true}]
      """.trimIndent()
    val touchpoints: List<F8eNotificationTouchpoint> = jsonDecoder.decodeFromString(json)
    touchpoints.first().shouldBeTypeOf<F8eEmailTouchpoint>()
      .email.shouldBe("a@b.com")
  }
})
