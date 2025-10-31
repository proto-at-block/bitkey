package build.wallet.f8e.recovery

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RemoteKeysetSerializerTests : FunSpec({

  val json = Json { ignoreUnknownKeys = true }

  val legacyKeyset =
    LegacyRemoteKeyset(
      keysetId = "legacy-keyset",
      networkType = "bitcoin-mainnet",
      appDescriptor = "legacy-app-dpub",
      hardwareDescriptor = "legacy-hardware-dpub",
      serverDescriptor = "legacy-server-dpub"
    )

  val privateMultisigKeyset =
    PrivateMultisigRemoteKeyset(
      keysetId = "private-keyset",
      networkType = "bitcoin-mainnet",
      appPublicKey = "private-app-pub",
      hardwarePublicKey = "private-hardware-pub",
      serverPublicKey = "private-server-pub"
    )

  test("serializes mixed remote keyset list with discriminator keys") {
    val encodedJson = json.encodeToString(listOf(legacyKeyset, privateMultisigKeyset))
    encodedJson.shouldBe(
      """[{"keyset_id":"legacy-keyset","network":"bitcoin-mainnet","app_dpub":"legacy-app-dpub","hardware_dpub":"legacy-hardware-dpub","server_dpub":"legacy-server-dpub"},{"keyset_id":"private-keyset","network":"bitcoin-mainnet","app_pub":"private-app-pub","hardware_pub":"private-hardware-pub","server_pub":"private-server-pub"}]"""
    )
  }

  test("deserializes mixed remote keyset list into concrete types") {
    val encodedJson =
      """
      [
        {
          "keyset_id": "legacy-keyset",
          "network": "bitcoin-mainnet",
          "app_dpub": "legacy-app-dpub",
          "hardware_dpub": "legacy-hardware-dpub",
          "server_dpub": "legacy-server-dpub"
        },
        {
          "keyset_id": "private-keyset",
          "network": "bitcoin-mainnet",
          "app_pub": "private-app-pub",
          "hardware_pub": "private-hardware-pub",
          "server_pub": "private-server-pub"
        }
      ]
      """.trimIndent()

    val decodedKeysets: List<RemoteKeyset> = json.decodeFromString(encodedJson)

    decodedKeysets.size.shouldBe(2)

    val decodedLegacyKeyset = decodedKeysets[0].shouldBeInstanceOf<LegacyRemoteKeyset>()
    decodedLegacyKeyset.keysetId.shouldBe("legacy-keyset")
    decodedLegacyKeyset.networkType.shouldBe("bitcoin-mainnet")
    decodedLegacyKeyset.appDescriptor.shouldBe("legacy-app-dpub")
    decodedLegacyKeyset.hardwareDescriptor.shouldBe("legacy-hardware-dpub")
    decodedLegacyKeyset.serverDescriptor.shouldBe("legacy-server-dpub")

    val decodedPrivateKeyset =
      decodedKeysets[1].shouldBeInstanceOf<PrivateMultisigRemoteKeyset>()
    decodedPrivateKeyset.keysetId.shouldBe("private-keyset")
    decodedPrivateKeyset.networkType.shouldBe("bitcoin-mainnet")
    decodedPrivateKeyset.appPublicKey.shouldBe("private-app-pub")
    decodedPrivateKeyset.hardwarePublicKey.shouldBe("private-hardware-pub")
    decodedPrivateKeyset.serverPublicKey.shouldBe("private-server-pub")
  }
})
