package build.wallet.encrypt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import okio.ByteString.Companion.encodeUtf8

class XSealedDataTests : FunSpec({
  test("XSealedData round trip") {
    val original =
      XSealedData(
        header = XSealedData.Header(algorithm = "unreal"),
        ciphertext = "ciphertext".encodeUtf8(),
        nonce = XNonce("nonce".encodeUtf8())
      )
    original.toOpaqueCiphertext().value.shouldBeEqual(
      "eyJhbGciOiJ1bnJlYWwifQ.Y2lwaGVydGV4dA.bm9uY2U"
    )
    val roundtrip = original.toOpaqueCiphertext().toXSealedData()
    roundtrip.shouldBeEqual(original)
  }
})
