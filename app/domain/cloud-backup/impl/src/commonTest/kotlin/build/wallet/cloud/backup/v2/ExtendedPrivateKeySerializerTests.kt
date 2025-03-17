package build.wallet.cloud.backup.v2

import build.wallet.bitcoin.keys.ExtendedPrivateKey
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ExtendedPrivateKeySerializerTests : FunSpec({

  val encodedString = """
    {
      "extendedPrivateKey": {
        "xprv": "orange",
        "mnemonics": "nothing rhymes with it-rus"
      }
    }
  """
  val decodedObject =
    ExtendedPrivateKeyHolder(
      ExtendedPrivateKey(
        xprv = "orange",
        mnemonic = "nothing rhymes with it-rus"
      )
    )

  test("serialize") {
    val encoded = Json.encodeToString(decodedObject)
    encoded.shouldEqualJson(encodedString)
  }

  test("deserialize") {
    val decoded = Json.decodeFromString<ExtendedPrivateKeyHolder>(encodedString)
    decoded.shouldBeEqual(decodedObject)
  }
})

@Serializable
private data class ExtendedPrivateKeyHolder(
  @Serializable(with = ExtendedPrivateKeySerializer::class)
  val extendedPrivateKey: ExtendedPrivateKey,
)
