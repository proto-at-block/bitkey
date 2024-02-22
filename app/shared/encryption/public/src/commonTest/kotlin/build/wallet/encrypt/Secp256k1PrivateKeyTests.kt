package build.wallet.encrypt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class Secp256k1PrivateKeyTests : FunSpec({
  val key = Secp256k1PrivateKey(bytes = "bytes1234".encodeUtf8())

  test("SecretKey is redacted") {
    "$key".shouldBe("Secp256k1PrivateKey(██)")
    key.toString().shouldBe("Secp256k1PrivateKey(██)")
  }
})
