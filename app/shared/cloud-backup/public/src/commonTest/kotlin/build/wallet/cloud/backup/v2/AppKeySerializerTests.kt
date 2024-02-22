package build.wallet.cloud.backup.v2

import build.wallet.bitkey.keys.app.AppKeyImpl
import build.wallet.crypto.CurveType
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

class AppKeySerializerTests : FunSpec({
  val serializer = AppKeySerializer
  val encodedString = """{"curveType":"SECP256K1","publicKey":"foo","privateKeyHex":"626172"}"""
  val decodedObject =
    AppKeyImpl(CurveType.SECP256K1, PublicKey("foo"), PrivateKey("bar".encodeUtf8()))

  test("serialize") {
    val encoded = Json.encodeToString(serializer, decodedObject)
    encoded.shouldBeEqual(encodedString)
  }

  test("deserialize") {
    val decoded = Json.decodeFromString(serializer, encodedString)
    decoded.shouldBeEqual(decodedObject)
  }
})
