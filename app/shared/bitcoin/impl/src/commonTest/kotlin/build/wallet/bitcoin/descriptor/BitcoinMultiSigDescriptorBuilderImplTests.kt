package build.wallet.bitcoin.descriptor

import build.wallet.bitcoin.descriptor.BitcoinDescriptor.Spending
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BitcoinMultiSigDescriptorBuilderImplTests : FunSpec({
  val descriptorBuilder = BitcoinMultiSigDescriptorBuilderImpl()

  context("change") {
    val appPrivateKey = ExtendedPrivateKey(xprv = "app-xprv", mnemonic = "app-mnemonic")
    val hardwarePublicKey =
      DescriptorPublicKey(
        dpub = "[34eae6a8/84'/0'/0']xpubDDj952KUFGTDcNV1qY5Tuevm6vnBWK8NSpTTkCz1XTApv2SeDaqcrUTBgDdCRF9KmtxV33R8E9NtSi9VSBUPj4M3fKr4uk3kRy8Vbo1LbAv/*"
      )
    val serverPublicKey =
      DescriptorPublicKey(
        dpub = "[3bef7db3/84'/0'/0']xpubDDU6hSKRg88wLi4rQfXAebcKed7T357BGdiLJQ8aoiuefBVD3CjrDN4XNfnBixs6nv5QgdCLR9FBRXfnsJWd8AnNVSEnGxqWK5YL5SFuCqh/*"
      )

    val appPrivateChangeKey =
      ExtendedPrivateKey(
        xprv = "app-xprv/1/*",
        mnemonic = "app-mnemonic"
      )
    val hardwarePublicChangeKey = DescriptorPublicKeyMock(identifier = "hw")
    val serverPublicChangeKey = DescriptorPublicKeyMock(identifier = "server")

    test("spending multisig descriptor") {
      descriptorBuilder.spendingChangeDescriptor(
        appPrivateKey = appPrivateKey,
        hardwareKey = hardwarePublicKey,
        serverKey = serverPublicKey
      )
        .shouldBe(
          Spending(
            "wsh(sortedmulti(2,app-xprv,[34eae6a8/84'/0'/0']xpubDDj952KUFGTDcNV1qY5Tuevm6vnBWK8NSpTTkCz1XTApv2SeDaqcrUTBgDdCRF9KmtxV33R8E9NtSi9VSBUPj4M3fKr4uk3kRy8Vbo1LbAv/1/*,[3bef7db3/84'/0'/0']xpubDDU6hSKRg88wLi4rQfXAebcKed7T357BGdiLJQ8aoiuefBVD3CjrDN4XNfnBixs6nv5QgdCLR9FBRXfnsJWd8AnNVSEnGxqWK5YL5SFuCqh/1/*))"
          )
        )
    }

    test("spending multisig descriptor for key with change child") {
      descriptorBuilder.spendingChangeDescriptor(
        appPrivateKey = appPrivateChangeKey,
        hardwareKey = hardwarePublicChangeKey,
        serverKey = serverPublicChangeKey
      ).shouldBe(
        Spending(
          "wsh(sortedmulti(2,app-xprv/1/*,[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fhw/1/*,[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKserver/1/*))"
        )
      )
    }
  }

  context("receiving") {
    val appPrivateKey = ExtendedPrivateKey(xprv = "app-xprv/*", mnemonic = "app-mnemonic")
    val hardwarePublicKey = DescriptorPublicKeyMock(identifier = "hw-xprv/*")
    val serverPublicKey = DescriptorPublicKeyMock(identifier = "server-dpub/*")

    val appPrivateReceivingKey =
      ExtendedPrivateKey(
        xprv = "app-xprv/0/*",
        mnemonic = "app-mnemonic"
      )
    val hardwarePublicReceivingKey = DescriptorPublicKeyMock(identifier = "hw")
    val serverPublicReceivingKey = DescriptorPublicKeyMock(identifier = "server")

    test("spending multisig descriptor") {
      descriptorBuilder.spendingReceivingDescriptor(
        appPrivateKey = appPrivateKey,
        hardwareKey = hardwarePublicKey,
        serverKey = serverPublicKey
      ).shouldBe(
        Spending(
          "wsh(sortedmulti(2,app-xprv/0/*,[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2Uryhwxprv/0/*/0/*,[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQserverdpub/0/*/0/*))"
        )
      )
    }

    test("spending multisig descriptor for key with receiving child") {
      descriptorBuilder.spendingReceivingDescriptor(
        appPrivateKey = appPrivateReceivingKey,
        hardwareKey = hardwarePublicReceivingKey,
        serverKey = serverPublicReceivingKey
      ).shouldBe(
        Spending(
          "wsh(sortedmulti(2,app-xprv/0/*,[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fhw/0/*,[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKserver/0/*))"
        )
      )
    }
  }
})
