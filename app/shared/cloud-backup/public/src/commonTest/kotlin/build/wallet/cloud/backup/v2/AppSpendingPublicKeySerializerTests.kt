package build.wallet.cloud.backup.v2

import build.wallet.bitkey.app.AppSpendingPublicKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AppSpendingPublicKeySerializerTests : FunSpec({

  val encodedString = """{"appSpendingPublicKey":"[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK/*"}"""
  val decodedObject =
    AppSpendingPublicKeyHolder(
      AppSpendingPublicKey(
        dpub = "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK/*"
      )
    )

  test("serialize") {
    val encoded = Json.encodeToString(decodedObject)
    encoded.shouldBeEqual(encodedString)
  }

  test("deserialize") {
    val decoded = Json.decodeFromString<AppSpendingPublicKeyHolder>(encodedString)
    decoded.shouldBeEqual(decodedObject)
  }
})

@Serializable
private data class AppSpendingPublicKeyHolder(
  @Serializable(with = AppSpendingPublicKeySerializer::class)
  val appSpendingPublicKey: AppSpendingPublicKey,
)
