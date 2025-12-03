package build.wallet.cloud.backup.csek

import build.wallet.crypto.SymmetricKeyImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class CsekTests : FunSpec({
  val csek = Csek(key = SymmetricKeyImpl(raw = "bytes1234".encodeUtf8()))

  test("Csek is redacted") {
    "$csek".shouldBe("Sek(key=SymmetricKeyImpl(raw=██))")
    csek.toString().shouldBe("Sek(key=SymmetricKeyImpl(raw=██))")
  }
})
