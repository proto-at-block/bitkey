package build.wallet.cloud.backup.v2

import build.wallet.bitkey.auth.AppGlobalAuthPrivateKeyMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.HwAuthPublicKeyMock
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.bitkey.spending.HwSpendingPublicKeyMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock2
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class CloudBackupBackwardCompatibilityTests : FunSpec({

  val fullAccountKeysWithoutKeysets = """
    {
        "activeSpendingKeyset": {
            "localId": "spending-public-keyset-fake-id-1",
            "keysetServerId": "f8e-spending-keyset-id",
            "appDpub": "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVappdpub/*",
            "hardwareDpub": "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQhardwaredpub/*",
            "serverDpub": "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2Userverdpub/*",
            "bitcoinNetworkType": "SIGNET"
        },
        "appGlobalAuthKeypair": {
            "publicKey": "app-auth-dpub",
            "privateKeyHex": "6170702d617574682d707269766174652d6b6579"
        },
        "appSpendingKeys": {
            "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVappdpub/*": {
                "xprv": "xprv123",
                "mnemonics": "mnemonic123"
            }
        },
        "activeHwSpendingKey": "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQhardwaredpub/*",
        "activeHwAuthKey": "hw-auth-dpub",
        "rotationAppGlobalAuthKeypair": null
    }
  """.trimIndent()

  test("deserialize old backup without keysets field") {
    val result = Json.decodeFromString<FullAccountKeys>(fullAccountKeysWithoutKeysets)

    result.activeSpendingKeyset shouldBe SpendingKeysetMock
    result.keysets shouldBe emptyList() // Should default to empty list for old backups
  }

  test("deserialize new backup with keysets field") {
    val fullAccountKeys = FullAccountKeys(
      activeSpendingKeyset = SpendingKeysetMock,
      keysets = listOf(SpendingKeysetMock, SpendingKeysetMock2),
      activeHwSpendingKey = HwSpendingPublicKeyMock,
      activeHwAuthKey = HwAuthPublicKeyMock,
      appGlobalAuthKeypair = AppKey(
        AppGlobalAuthPublicKeyMock,
        AppGlobalAuthPrivateKeyMock
      ),
      appSpendingKeys = mapOf(
        AppSpendingPublicKeyMock to
          AppSpendingPrivateKeyMock
      ),
      rotationAppGlobalAuthKeypair = null
    )

    // Serialize and then deserialize
    val json = Json.encodeToString(FullAccountKeys.serializer(), fullAccountKeys)
    val deserializedKeys = Json.decodeFromString<FullAccountKeys>(json)

    deserializedKeys.activeSpendingKeyset shouldBe SpendingKeysetMock
    deserializedKeys.keysets shouldBe listOf(SpendingKeysetMock, SpendingKeysetMock2)
  }
})
