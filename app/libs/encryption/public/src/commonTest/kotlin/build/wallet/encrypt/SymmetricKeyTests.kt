package build.wallet.encrypt

import build.wallet.crypto.SymmetricKeyImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class SymmetricKeyTests : FunSpec({
  val key = SymmetricKeyImpl(raw = "bytes1234".encodeUtf8())

  test("SymmetricKey is redacted") {
    "$key".shouldBe("SymmetricKeyImpl(██)")
    key.toString().shouldBe("SymmetricKeyImpl(██)")
  }
})
