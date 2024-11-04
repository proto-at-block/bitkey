package build.wallet.database.adapters

import build.wallet.encrypt.XCiphertext
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class XCiphertextColumnAdapterTests : FunSpec({

  val ciphertext = XCiphertext("xciphertext")

  test("decode ClaimId from string") {
    XCiphertextColumnAdapter.decode(ciphertext.value).shouldBe(ciphertext)
  }

  test("encode ClaimId to string") {
    XCiphertextColumnAdapter.encode(ciphertext).shouldBe(ciphertext.value)
  }
})
