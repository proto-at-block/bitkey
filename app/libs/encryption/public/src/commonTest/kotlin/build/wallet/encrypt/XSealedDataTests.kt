package build.wallet.encrypt

import build.wallet.crypto.PublicKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class XSealedDataTests : FunSpec({
  test("XSealedData round trip - Standard format") {
    val original = XSealedData(
      header = XSealedData.Header(algorithm = "unreal"),
      ciphertext = "ciphertext".encodeUtf8(),
      nonce = XNonce("nonce".encodeUtf8())
    )
    original.toOpaqueCiphertext().value.shouldBe(
      "eyJhbGciOiJ1bnJlYWwifQ.Y2lwaGVydGV4dA.bm9uY2U"
    )
    val roundtrip = original.toOpaqueCiphertext().toXSealedData()
    roundtrip.shouldBe(original)
  }

  test("XSealedData round trip - WithPubkey format") {
    val pubkey = PublicKey<Nothing>("aabbcc")
    val original = XSealedData(
      header = XSealedData.Header(format = XSealedData.Format.WithPubkey, algorithm = "unreal"),
      ciphertext = "ciphertext".encodeUtf8(),
      nonce = XNonce("nonce".encodeUtf8()),
      publicKey = pubkey
    )
    val expected = "eyJ2IjoyLCJhbGciOiJ1bnJlYWwifQ.Y2lwaGVydGV4dA.bm9uY2U.qrvM"
    original.toOpaqueCiphertext().value.shouldBe(expected)
    val roundtrip = original.toOpaqueCiphertext().toXSealedData()
    roundtrip.shouldBe(original)
  }
})
