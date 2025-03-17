package build.wallet.cloud.backup.v2

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SpendingKeysetSerializerTests : FunSpec({

  val encodedString = """
    {
      "spendingKeyset": {
        "localId": "foo",
        "keysetServerId": "keyset-id",
        "appDpub": "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK/*",
        "hardwareDpub": "[deadbeef/0h/1h/2h]xpub6ERApfZwUNrhLCkDtcHTcxd75RbzS1ed54G1LkBUHQVHQKqhMkhgbmJbZRkrgZw4koxb5JaHWkY4ALHY2grBGRjaDMzQLcgJvLJuZZvRcEL/3h/4h/5h/*h",
        "serverDpub": "[34eae6a8/84'/0'/0']xpubDDj952KUFGTDcNV1qY5Tuevm6vnBWK8NSpTTkCz1XTApv2SeDaqcrUTBgDdCRF9KmtxV33R8E9NtSi9VSBUPj4M3fKr4uk3kRy8Vbo1LbAv/*",
        "bitcoinNetworkType": "TESTNET"
      }
    }
  """
  val decodedObject =
    SpendingKeysetHolder(
      SpendingKeyset(
        localId = "foo",
        networkType = BitcoinNetworkType.TESTNET,
        appKey =
          AppSpendingPublicKey(
            dpub = "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK/*"
          ),
        hardwareKey =
          HwSpendingPublicKey(
            dpub = "[deadbeef/0h/1h/2h]xpub6ERApfZwUNrhLCkDtcHTcxd75RbzS1ed54G1LkBUHQVHQKqhMkhgbmJbZRkrgZw4koxb5JaHWkY4ALHY2grBGRjaDMzQLcgJvLJuZZvRcEL/3h/4h/5h/*h"
          ),
        f8eSpendingKeyset =
          F8eSpendingKeyset(
            keysetId = "keyset-id",
            spendingPublicKey =
              F8eSpendingPublicKey(
                dpub = "[34eae6a8/84'/0'/0']xpubDDj952KUFGTDcNV1qY5Tuevm6vnBWK8NSpTTkCz1XTApv2SeDaqcrUTBgDdCRF9KmtxV33R8E9NtSi9VSBUPj4M3fKr4uk3kRy8Vbo1LbAv/*"
              )
          )
      )
    )

  test("serialize") {
    val encoded = Json.encodeToString(decodedObject)
    encoded.shouldEqualJson(encodedString)
  }

  test("deserialize") {
    val decoded = Json.decodeFromString<SpendingKeysetHolder>(encodedString)
    decoded.shouldBeEqual(decodedObject)
  }
})

@Serializable
private data class SpendingKeysetHolder(
  @Serializable(with = SpendingKeysetSerializer::class)
  val spendingKeyset: SpendingKeyset,
)
