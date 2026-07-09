package build.wallet.nfc

import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.keys.DescriptorPublicKey.Origin
import build.wallet.bitcoin.keys.DescriptorPublicKey.Wildcard.Unhardened
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
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

  test("buildFor returns null for current account index by default") {
    val builder = SweepSigningContextBuilderImpl()

    builder.buildFor(
      oldKeyset = keyset(accountIndex = 0u),
      currentAccountIndex = 0u
    ).shouldBe(null)
  }

  test("buildFor returns context for non-current account index") {
    val builder = SweepSigningContextBuilderImpl()

    val context = builder.buildFor(
      oldKeyset = keyset(accountIndex = 1u),
      currentAccountIndex = 0u
    )

    context?.oldAccountIndex.shouldBe(1u)
  }
})

private fun keyset(accountIndex: UInt): SpendingKeyset {
  val descriptor = descriptorPublicKey(accountIndex)
  return SpendingKeyset(
    localId = "keyset-$accountIndex",
    networkType = SIGNET,
    appKey = AppSpendingPublicKey(descriptor),
    hardwareKey = HwSpendingPublicKey(descriptor),
    f8eSpendingKeyset = F8eSpendingKeyset(
      keysetId = "server-keyset-$accountIndex",
      spendingPublicKey = F8eSpendingPublicKey(descriptor),
      privateWalletRootXpub = VALID_XPUB
    )
  )
}

private fun descriptorPublicKey(accountIndex: UInt): DescriptorPublicKey =
  DescriptorPublicKey(
    origin = Origin(
      fingerprint = "e5ff120e",
      derivationPath = "/84'/0'/$accountIndex'"
    ),
    xpub = VALID_XPUB,
    derivationPath = "/*",
    wildcard = Unhardened
  )

private const val VALID_XPUB =
  "xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ESFjqJoCu1Rupje8YtGqsefD265TMg7usUDFdp6W1EGMcet8"
