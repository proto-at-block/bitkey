package build.wallet.bitcoin.keys

import build.wallet.bitkey.app.AppGlobalAuthPrivateKey
import build.wallet.encrypt.Secp256k1PrivateKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class AppAuthPrivateKeyTests : FunSpec({
  val key = AppGlobalAuthPrivateKey(Secp256k1PrivateKey("1234".encodeUtf8()))
  test("secret bytes are redacted") {
    "$key".shouldBe("AppGlobalAuthPrivateKey(██)")
    key.toString().shouldBe("AppGlobalAuthPrivateKey(██)")
  }
})
