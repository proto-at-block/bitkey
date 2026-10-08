package build.wallet.bitcoin.descriptor

import build.wallet.bitcoin.descriptor.BitcoinDescriptor.Spending
import build.wallet.bitcoin.descriptor.BitcoinDescriptor.Watching
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

class AppKeyWalletDescriptorBuilderImplTests : FunSpec({
  val builder = AppKeyWalletDescriptorBuilderImpl()

  // Test-only key expressions. Not real keys and not derived from customer data.
  val bareKey = ExtendedPrivateKey(xprv = "xprv-test", mnemonic = "test-mnemonic")
  val wildcardKey = ExtendedPrivateKey(xprv = "xprv-test/*", mnemonic = "test-mnemonic")
  val receivingKey = ExtendedPrivateKey(xprv = "xprv-test/0/*", mnemonic = "test-mnemonic")
  val changeKey = ExtendedPrivateKey(xprv = "xprv-test/1/*", mnemonic = "test-mnemonic")

  context("receiving descriptor") {
    test("appends chain and wildcard to a bare account key") {
      builder.receivingDescriptor(bareKey)
        .shouldBe(Spending("wpkh(xprv-test/0/*)"))
    }

    test("splices chain into a key that already has a wildcard") {
      builder.receivingDescriptor(wildcardKey)
        .shouldBe(Spending("wpkh(xprv-test/0/*)"))
    }

    test("is idempotent for a key already on the receiving chain") {
      builder.receivingDescriptor(receivingKey)
        .shouldBe(Spending("wpkh(xprv-test/0/*)"))
    }

    test("replaces an existing change chain with the receiving chain") {
      builder.receivingDescriptor(changeKey)
        .shouldBe(Spending("wpkh(xprv-test/0/*)"))
    }
  }

  context("change descriptor") {
    test("appends chain and wildcard to a bare account key") {
      builder.changeDescriptor(bareKey)
        .shouldBe(Spending("wpkh(xprv-test/1/*)"))
    }

    test("splices chain into a key that already has a wildcard") {
      builder.changeDescriptor(wildcardKey)
        .shouldBe(Spending("wpkh(xprv-test/1/*)"))
    }

    test("is idempotent for a key already on the change chain") {
      builder.changeDescriptor(changeKey)
        .shouldBe(Spending("wpkh(xprv-test/1/*)"))
    }

    test("replaces an existing receiving chain with the change chain") {
      builder.changeDescriptor(receivingKey)
        .shouldBe(Spending("wpkh(xprv-test/1/*)"))
    }
  }

  test("receiving and change descriptors target different chains") {
    // Guards against a sweep that scans the change chain twice and misses receiving funds.
    builder.receivingDescriptor(bareKey).raw
      .shouldBe("wpkh(xprv-test/0/*)")
    builder.changeDescriptor(bareKey).raw
      .shouldBe("wpkh(xprv-test/1/*)")
  }

  test("descriptors are native segwit single-sig, never multisig") {
    // Rescue descriptors must be spendable by the app key alone; sortedmulti would require a
    // second signature and the sweep could never be completed locally.
    val raw = builder.receivingDescriptor(bareKey).raw
    raw.shouldStartWith("wpkh(")
    raw.shouldContain("xprv-test")
    raw.contains("sortedmulti").shouldBe(false)
    raw.contains("wsh(").shouldBe(false)
  }

  test("always produces a wildcard so scanning covers more than one address") {
    listOf(bareKey, wildcardKey, receivingKey).forEach { key ->
      builder.receivingDescriptor(key).raw.shouldContain("/*")
    }
  }

  context("watching descriptor") {
    val dpub =
      "[34eae6a8/84'/0'/0']xpubDDj952KUFGTDcNV1qY5Tuevm6vnBWK8NSpTTkCz1XTApv2SeDaqcrUTBgDdCRF9KmtxV33R8E9NtSi9VSBUPj4M3fKr4uk3kRy8Vbo1LbAv"
    val publicKey = DescriptorPublicKey(dpub)

    test("defaults to the receiving chain") {
      builder.watchingDescriptor(publicKey)
        .shouldBe(Watching("wpkh($dpub/0/*)"))
    }

    test("targets the change chain when requested") {
      builder.watchingDescriptor(publicKey, isChange = true)
        .shouldBe(Watching("wpkh($dpub/1/*)"))
    }

    test("contains no private key material") {
      builder.watchingDescriptor(publicKey).raw
        .contains("xprv").shouldBe(false)
    }

  }
})
