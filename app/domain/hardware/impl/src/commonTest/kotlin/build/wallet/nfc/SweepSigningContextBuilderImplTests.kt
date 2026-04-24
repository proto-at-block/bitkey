package build.wallet.nfc

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * BIP-32 test vector 1 (https://en.bitcoin.it/wiki/BIP_0032_TestVectors):
 * master xpub `xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ESFjqJoCu1Rupje8YtGqsefD265TMg7usUDFdp6W1EGMcet8`
 *   chaincode = 873dff81c02f525623fd1fe5167eac3a55a049de3d314bb42ee227ffed37d508
 *   pubkey    = 0339a36013301597daef41fbe593a02cc513d0b55527ec2df1050e2e8ff49c85c2
 */
class SweepSigningContextBuilderImplTests : FunSpec({
  test("decodeXpubMaterial extracts pubkey + chaincode from a BIP-32 test vector") {
    val xpub =
      "xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ESFjqJoCu1Rupje8YtGqsefD265TMg7usUDFdp6W1EGMcet8"

    val material = decodeXpubMaterial(xpub)

    material.pubkey.hex().shouldBe(
      "0339a36013301597daef41fbe593a02cc513d0b55527ec2df1050e2e8ff49c85c2"
    )
    material.chaincode.hex().shouldBe(
      "873dff81c02f525623fd1fe5167eac3a55a049de3d314bb42ee227ffed37d508"
    )
    material.pubkey.size.shouldBe(33)
    material.chaincode.size.shouldBe(32)
  }

  test("decodeXpubMaterial rejects a tampered xpub (checksum mismatch)") {
    // Last char flipped — checksum fails.
    val tampered =
      "xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ESFjqJoCu1Rupje8YtGqsefD265TMg7usUDFdp6W1EGMcet9"

    try {
      decodeXpubMaterial(tampered)
      throw AssertionError("expected IllegalArgumentException")
    } catch (_: IllegalArgumentException) {
      // expected
    }
  }
})
