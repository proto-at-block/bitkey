package build.wallet.component.bitcoin.keys

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.keys.DescriptorPublicKey.Wildcard.Unhardened
import build.wallet.bitcoin.keys.ExtendedKeyGenerator
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum

class ExtendedKeyGeneratorComponentTests : FunSpec({
  val app = launchNewApp().app
  val extendedKeyGenerator: ExtendedKeyGenerator = app.extendedKeyGenerator

  context("generate new extended key") {
    checkAll(Exhaustive.enum<BitcoinNetworkType>()) { network ->
      val keypair =
        extendedKeyGenerator
          .generate(network)
          .shouldBeOk()

      test("validate xpub - starts with xpub/tpub prefix") {
        when (network) {
          BITCOIN -> keypair.publicKey.xpub.shouldStartWith("xpub")
          else -> keypair.publicKey.xpub.shouldStartWith("tpub")
        }
      }

      test("validate dpub - contains origin derivation") {
        when (network) {
          BITCOIN -> keypair.publicKey.origin.derivationPath.shouldBe("/84'/0'/0'")
          else -> keypair.publicKey.origin.derivationPath.shouldBe("/84'/1'/0'")
        }
      }

      test("dpub - validate derivation path") {
        keypair.publicKey.derivationPath.shouldBe("/*")
      }

      test("dpub - validate wildcard") {
        keypair.publicKey.wildcard.shouldBe(Unhardened)
      }

      test("validate xprv - contains xprv/tprv") {
        when (network) {
          BITCOIN -> keypair.privateKey.xprv.shouldContain("xprv")
          else -> keypair.privateKey.xprv.shouldContain("tprv")
        }
      }
    }
  }
})
