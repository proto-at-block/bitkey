package build.wallet.bitcoin.keys

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.crypto.PrivateKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class AppAuthPrivateKeyTests : FunSpec({
  val key = PrivateKey<AppGlobalAuthKey>("1234".encodeUtf8())
  test("secret bytes are redacted") {
    "$key".shouldBe("PrivateKey(██)")
    key.toString().shouldBe("PrivateKey(██)")
  }
})
