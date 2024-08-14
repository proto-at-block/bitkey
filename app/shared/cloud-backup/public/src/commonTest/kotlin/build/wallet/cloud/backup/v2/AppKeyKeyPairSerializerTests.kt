package build.wallet.cloud.backup.v2

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

class AppKeyKeyPairSerializerTests : FunSpec({
  val serializer = AppKeyKeyPairSerializer<DelegatedDecryptionKey>()
  val encodedString = """{"publicKey":"foo","privateKeyHex":"626172"}"""
  val decodedObject =
    AppKey<DelegatedDecryptionKey>(PublicKey("foo"), PrivateKey("bar".encodeUtf8()))

  test("serialize") {
    val encoded = Json.encodeToString(serializer, decodedObject)
    encoded.shouldBeEqual(encodedString)
  }

  test("deserialize") {
    val decoded = Json.decodeFromString(serializer, encodedString)
    decoded.shouldBeEqual(decodedObject)
  }
})
