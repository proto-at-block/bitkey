package build.wallet.encrypt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class SealedDataTests : FunSpec({
  val data =
    SealedData(
      ciphertext = "ciphertext".encodeUtf8(),
      nonce = "nonce".encodeUtf8(),
      tag = "tag".encodeUtf8()
    )

  test("SealedData is redacted") {
    "$data".shouldBe("SealedData(██)")
    data.toString().shouldBe("SealedData(██)")
  }
})
