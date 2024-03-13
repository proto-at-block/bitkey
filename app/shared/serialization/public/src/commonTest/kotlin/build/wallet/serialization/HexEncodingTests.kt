package build.wallet.serialization

import build.wallet.serialization.hex.decodeHexWithResult
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HexEncodingTests : FunSpec({

  context("decodeHexWithResult") {
    test("ok") {
      "deadbeef".decodeHexWithResult().shouldBeOk().toByteArray()
        .shouldBe(byteArrayOf(-34, -83, -66, -17))
    }

    test("err") {
      "alivebeef".decodeHexWithResult().shouldBeErrOfType<IllegalArgumentException>()
        .message.shouldBe("Unexpected hex string: alivebeef")
    }
  }
})
