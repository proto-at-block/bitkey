package build.wallet.bitcoin.keys

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ExtendingPrivateKeyTests : FunSpec({
  val key =
    ExtendedPrivateKey(
      xprv = "xprv1234",
      mnemonic = "mnemonic1234"
    )
  context("redact") {
    test("xprv and mnemonics are redacted") {
      "$key".shouldBe("ExtendedPrivateKey(██)")
      key.toString().shouldBe("ExtendedPrivateKey(██)")
    }
  }
})
