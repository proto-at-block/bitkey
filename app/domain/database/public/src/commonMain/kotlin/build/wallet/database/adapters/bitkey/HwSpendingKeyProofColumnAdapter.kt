package build.wallet.database.adapters.bitkey

import app.cash.sqldelight.ColumnAdapter
import build.wallet.bitkey.hardware.HwAttestationCertificate
import build.wallet.bitkey.hardware.HwSpendingKeyAttestationSignature
import build.wallet.bitkey.hardware.HwSpendingKeyProof
import build.wallet.catchingResult
import build.wallet.logging.logWarn
import com.github.michaelbull.result.getOrElse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Persists [HwSpendingKeyProof] as a JSON string. The proof is already
 * base64-encoded on the wire (`signature` + each `certChain` entry), so JSON
 * is a safe transport without further encoding.
 *
 * On decode failure (schema drift, hand-edited row, partial write) we log and
 * return a sentinel empty proof rather than crashing the entity hydration
 * that the recovery / migration resume paths depend on. Consumers must treat
 * an empty-signature proof as equivalent to a missing proof — see
 * [HwSpendingKeyProof.isUsable].
 */
internal object HwSpendingKeyProofColumnAdapter : ColumnAdapter<HwSpendingKeyProof, String> {
  private val json = Json { explicitNulls = false }

  override fun decode(databaseValue: String): HwSpendingKeyProof {
    return catchingResult {
      val jsonObject = json.parseToJsonElement(databaseValue).jsonObject
      HwSpendingKeyProof(
        signature = HwSpendingKeyAttestationSignature(
          jsonObject.getValue("signature").jsonPrimitive.content
        ),
        certChain = jsonObject
          .getValue("certChain")
          .jsonArray
          .map { HwAttestationCertificate(it.jsonPrimitive.content) }
      )
    }.getOrElse {
      logWarn(throwable = it) { "Failed to decode HwSpendingKeyProof; returning empty proof" }
      HwSpendingKeyProof(
        signature = HwSpendingKeyAttestationSignature(""),
        certChain = emptyList()
      )
    }
  }

  override fun encode(value: HwSpendingKeyProof): String {
    val element =
      buildJsonObject {
        put("signature", JsonPrimitive(value.signature.value))
        put(
          "certChain",
          buildJsonArray {
            value.certChain.forEach { add(JsonPrimitive(it.value)) }
          }
        )
      }
    return element.toString()
  }
}
