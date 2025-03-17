package build.wallet.crypto

import build.wallet.rust.core.NoiseRole
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeHex
import build.wallet.rust.core.HardwareBackedDh as CoreHardwareBackedDh
import build.wallet.rust.core.HardwareBackedKeyPair as CoreHardwareBackedKeyPair
import build.wallet.rust.core.PrivateKey as CorePrivateKey

@Suppress("VariableNaming") // The constants are much easier to read in upper snake case.
class NoiseContextTests :
  FunSpec({
    // The following are test vectors generated from Python code doing ECDH-P256. This prevents us
    // from needing a Kotlin DH implementation, and so we can have repeatable tests.
    val CLIENT_E_SK = "0daa5d2ab35b08daec2995c10eb2d115137d65c1cf36d33fea20aafc09bfc3eb"
    val CLIENT_E_PK =
      "04a5a9e166af82c22553d84680b770885652cc9e74e9303387ab082bde68a2b5e8cc42768ba7f7d09b0f1e69d77accf387144a23b99f5398fea0b2cb4bf86b5164"
        .decodeHex()
        .toByteArray()
    val SERVER_E_SK = "e1927714ec97dc63b6b6c8214ba6fd883378609bd6fb763dc47c2df10f4b573e"
    val SERVER_E_PK =
      "0448e6cdd4041a85f1c98262095bb27fca1b4f8d3d39ce8c9f37bee88f6590d34cbb8b9085cbfbcc4456af28c2f8c5474fb6126be74bc1e39379d92665650867d6"
        .decodeHex()
        .toByteArray()
    val SHARED_SECRET_EE = "710e35285cb9b3cee4147554b438d730bbf8b06f5f6a4a7aac7c0c60dd16cfcf"
      .decodeHex()
      .toByteArray()

    val INVALID_SK = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    val INVALID_PK = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
      .decodeHex()
      .toByteArray()

    val CLIENT_S_SK = "694838b65a6532ea0daefbdf5741c59f2e909db8361efde0dab125212b474db3"
    val CLIENT_S_PK =
      "04d7e3c7903a095bb9785bcf05779f3dca227d4bdca0e59ad0d4c0401c054abc1c6120218f8467c1621e7c13d8fa4b1e6a0a9a870b9cb02dc90c78bd751dd5f575"
        .decodeHex()
        .toByteArray()
    val SERVER_R_SK = "6201de406564c2eff549ed999c513fa0108fb4b080971aa30f263b1556824936"
    val SERVER_R_PK =
      "04eb22622176c7e622abc9df700b69f26fb2f0ca8f5718e5172da90bf8926aa44fac467d6d462e5f7da565b73e6eb0d102ccdec3fe653a626a6dd13ecb57c02111"
        .decodeHex()
        .toByteArray()
    val SHARED_SECRET_SR = "6c2ab2a9a3c617d2066a4692bdee3116ba42d25fcb63750b1213086f986c0ace"
      .decodeHex()
      .toByteArray()

    // Client S 694838b65a6532ea0daefbdf5741c59f2e909db8361efde0dab125212b474db3
    // Server E 0248e6cdd4041a85f1c98262095bb27fca1b4f8d3d39ce8c9f37bee88f6590d34c
    val SHARED_SECRET_SE = "cd6770858c824ccf05e807319132d486546732214d21c77b807c3e75a4fb55ae"
      .decodeHex()
      .toByteArray()

    // Server R 6201de406564c2eff549ed999c513fa0108fb4b080971aa30f263b1556824936
    // Client E 02a5a9e166af82c22553d84680b770885652cc9e74e9303387ab082bde68a2b5e8
    val SHARED_SECRET_RE = "6f6beb0751735a098ffd5ab5540ab93e196712a2612c079595b478cb621c5409"
      .decodeHex()
      .toByteArray()

    // Fake secure enclave implementation. We're abusing the API here; passing in hex-encoded
    // private keys, but it works out and lets us test the underlying implementation.
    class FakeCoreHardwareBackedDhImpl(
      private val client: Boolean,
    ) : CoreHardwareBackedDh {
      override fun dh(
        ourPrivkeyName: String,
        peerPubkey: ByteArray,
      ): ByteArray {
        val trimmedPubKey = peerPubkey
        return when {
          ourPrivkeyName.contentEquals(CLIENT_E_SK) && trimmedPubKey.contentEquals(SERVER_E_PK) -> SHARED_SECRET_EE
          ourPrivkeyName.contentEquals(SERVER_E_SK) && trimmedPubKey.contentEquals(CLIENT_E_PK) -> SHARED_SECRET_EE
          ourPrivkeyName.contentEquals(CLIENT_S_SK) && trimmedPubKey.contentEquals(SERVER_E_PK) -> SHARED_SECRET_SE
          ourPrivkeyName.contentEquals(SERVER_E_SK) && trimmedPubKey.contentEquals(CLIENT_S_PK) -> SHARED_SECRET_SE
          ourPrivkeyName.contentEquals(CLIENT_E_SK) && trimmedPubKey.contentEquals(SERVER_R_PK) -> SHARED_SECRET_RE
          ourPrivkeyName.contentEquals(SERVER_R_SK) && trimmedPubKey.contentEquals(CLIENT_E_PK) -> SHARED_SECRET_RE
          ourPrivkeyName.contentEquals(SERVER_R_SK) && trimmedPubKey.contentEquals(CLIENT_S_PK) -> SHARED_SECRET_SR
          ourPrivkeyName.contentEquals(CLIENT_S_SK) && trimmedPubKey.contentEquals(SERVER_R_PK) -> SHARED_SECRET_SR
          else -> throw IllegalArgumentException(
            "Invalid key pair combination: " +
              "$ourPrivkeyName, " +
              "${trimmedPubKey.toHexString()}, " +
              peerPubkey.toHexString()
          )
        }
      }

      override fun generate(): CoreHardwareBackedKeyPair =
        if (client) {
          CoreHardwareBackedKeyPair(CLIENT_E_SK, CLIENT_E_PK)
        } else {
          CoreHardwareBackedKeyPair(SERVER_E_SK, SERVER_E_PK)
        }

      override fun pubkey(privkeyName: String): ByteArray =
        when {
          privkeyName.contentEquals(CLIENT_E_SK) -> CLIENT_E_PK
          privkeyName.contentEquals(CLIENT_S_SK) -> CLIENT_S_PK
          privkeyName.contentEquals(SERVER_E_SK) -> SERVER_E_PK
          privkeyName.contentEquals(SERVER_R_SK) -> SERVER_R_PK
          else -> throw IllegalArgumentException("Invalid private key: $privkeyName")
        }
    }

    fun buildServerAndClient(): Pair<NoiseContextImpl, NoiseContextImpl> {
      // We have to specify client vs server in tests so that we can pick the right hardcoded keys.
      val hardwareBackedDhClient = FakeCoreHardwareBackedDhImpl(client = true)
      val hardwareBackedDhServer = FakeCoreHardwareBackedDhImpl(client = false)

      val client =
        NoiseContextImpl(
          NoiseRole.INITIATOR,
          CorePrivateKey.HardwareBacked(CLIENT_S_SK),
          SERVER_R_PK,
          hardwareBackedDhClient
        )
      val server =
        NoiseContextImpl(
          NoiseRole.RESPONDER,
          CorePrivateKey.HardwareBacked(SERVER_R_SK),
          null,
          hardwareBackedDhServer
        )

      return Pair(client, server)
    }

    test("successful handshake and encryption") {
      val (client, server) = buildServerAndClient()

      val c2s = client.initiateHandshake()
      val s2c = server.advanceHandshake(c2s)
      client.advanceHandshake(s2c!!)

      client.finalizeHandshake()
      server.finalizeHandshake()

      val clientMessage = "client -> server".toByteArray()
      val serverMessage = "server -> client".toByteArray()

      val encryptedClientMessage = client.encryptMessage(clientMessage)
      val decryptedClientMessage = server.decryptMessage(encryptedClientMessage)
      decryptedClientMessage shouldBe clientMessage

      val encryptedServerMessage = server.encryptMessage(serverMessage)
      val decryptedServerMessage = client.decryptMessage(encryptedServerMessage)
      decryptedServerMessage shouldBe serverMessage
    }

    test("invalid server public key") {
      val hardwareBackedDhClient = FakeCoreHardwareBackedDhImpl(client = true)
      shouldThrow<Exception> {
        val client =
          NoiseContextImpl(
            NoiseRole.INITIATOR,
            CorePrivateKey.HardwareBacked(CLIENT_S_SK),
            INVALID_PK,
            hardwareBackedDhClient
          )
        client.initiateHandshake()
      }
    }

    test("invalid client private key") {
      val hardwareBackedDhServer = FakeCoreHardwareBackedDhImpl(client = false)
      shouldThrow<Exception> {
        val server =
          NoiseContextImpl(
            NoiseRole.RESPONDER,
            CorePrivateKey.HardwareBacked(INVALID_SK),
            null,
            hardwareBackedDhServer
          )
        server.initiateHandshake()
      }
    }

    test("corrupted handshake message") {
      val (client, server) = buildServerAndClient()

      val c2s = client.initiateHandshake()
      val corruptedC2S = c2s.copyOf().apply {
        this[0] = (this[0].toInt() xor 0xFF).toByte()
      } // Corrupt the message

      shouldThrow<Exception> {
        server.advanceHandshake(corruptedC2S)
      }
    }

    test("same key encrypt decrypt") {
      val (client, server) = buildServerAndClient()

      val c2s = client.initiateHandshake()
      val s2c = server.advanceHandshake(c2s)
      client.advanceHandshake(s2c!!)

      client.finalizeHandshake()
      server.finalizeHandshake()

      val invalidMessage = "invalid message".toByteArray()
      val encryptedInvalidMessage = server.encryptMessage(invalidMessage)

      shouldThrow<Exception> {
        server.decryptMessage(encryptedInvalidMessage)
      }
    }

    test("handshake timeout or invalid state") {
      val (client, _) = buildServerAndClient()
      val c2s = client.initiateHandshake()
      shouldThrow<Exception> {
        client.advanceHandshake(c2s)
      }
    }

    test("inconsistent handshake roles") {
      val hardwareBackedDhClient = FakeCoreHardwareBackedDhImpl(client = true)
      val hardwareBackedDhServer = FakeCoreHardwareBackedDhImpl(client = false)

      val client =
        NoiseContextImpl(
          NoiseRole.INITIATOR,
          CorePrivateKey.HardwareBacked(CLIENT_S_SK),
          SERVER_R_PK,
          hardwareBackedDhClient
        )
      val anotherClient =
        NoiseContextImpl(
          NoiseRole.INITIATOR, // Incorrect role
          CorePrivateKey.HardwareBacked(SERVER_R_SK),
          SERVER_R_PK,
          hardwareBackedDhServer
        )

      val c2s = client.initiateHandshake()

      shouldThrow<Exception> {
        anotherClient.advanceHandshake(c2s)
      }
    }
  })
