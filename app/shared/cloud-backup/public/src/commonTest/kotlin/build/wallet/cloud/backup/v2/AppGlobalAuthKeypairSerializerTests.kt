package build.wallet.cloud.backup.v2

import build.wallet.bitkey.app.AppGlobalAuthKeypair
import build.wallet.bitkey.app.AppGlobalAuthPrivateKey
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.encrypt.Secp256k1PrivateKey
import build.wallet.encrypt.Secp256k1PublicKey
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

class AppGlobalAuthKeypairSerializerTests : FunSpec({

  val serializer = AppGlobalAuthKeypairSerializer
  val encodedString = """
    {
      "publicKey": "foo",
      "privateKeyHex": "626172"
    }
  """
  val decodedObject =
    AppGlobalAuthKeypair(
      AppGlobalAuthPublicKey(Secp256k1PublicKey("foo")),
      AppGlobalAuthPrivateKey(Secp256k1PrivateKey("bar".encodeUtf8()))
    )

  test("serialize") {
    val encoded = Json.encodeToString(serializer, decodedObject)
    encoded.shouldEqualJson(encodedString)
  }

  test("deserialize") {
    val decoded = Json.decodeFromString(serializer, encodedString)
    decoded.shouldBeEqual(decodedObject)
  }
})
