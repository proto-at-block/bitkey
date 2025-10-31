package build.wallet.bitkey.f8e

import build.wallet.bitcoin.keys.DescriptorPublicKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class F8eSpendingPublicKeySerializerTests : FunSpec({
  val json = Json
  val dpub =
    "[deadbeef/0h/1h/2h]xpub6ERApfZwUNrhLCkDtcHTcxd75RbzS1ed54G1LkBUHQVHQKqhMkhgbm" +
      "JbZRkrgZw4koxb5JaHWkY4ALHY2grBGRjaDMzQLcgJvLJuZZvRcEL/3h/4h/5h/*h"

  test("encodes to raw spending descriptor string") {
    val key = F8eSpendingPublicKey(DescriptorPublicKey(dpub))

    json.encodeToString(key) shouldBe "\"$dpub\""
  }

  test("decodes from raw spending descriptor string") {
    val decoded = json.decodeFromString<F8eSpendingPublicKey>("\"$dpub\"")

    decoded.key.dpub shouldBe dpub
  }
})
