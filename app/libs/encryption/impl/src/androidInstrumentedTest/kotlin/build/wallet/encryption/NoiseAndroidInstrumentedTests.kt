package build.wallet.encryption

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import build.wallet.crypto.HardwareBackedDhImpl
import build.wallet.crypto.NoiseContextImpl
import build.wallet.rust.core.NoiseRole
import build.wallet.secureenclave.SeKeyPurpose.AGREEMENT
import build.wallet.secureenclave.SeKeyPurposes
import build.wallet.secureenclave.SeKeySpec
import build.wallet.secureenclave.SeKeyUsageConstraints
import build.wallet.secureenclave.SecureEnclaveImpl
import build.wallet.secureenclave.SecureEnclaveImpl.Companion.encodePublicKeyAsSEC1Uncompressed
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import build.wallet.rust.core.PrivateKey as CorePrivateKey

@RunWith(AndroidJUnit4::class)
class NoiseAndroidInstrumentedTests {
  private fun handshakeAndMessage(
    client: NoiseContextImpl,
    server: NoiseContextImpl,
  ) {
    val c2s = client.initiateHandshake()
    val s2c = server.advanceHandshake(c2s)
    client.advanceHandshake(requireNotNull(s2c))

    client.finalizeHandshake()
    server.finalizeHandshake()

    val clientMessage = "client -> server".toByteArray()
    val serverMessage = "server -> client".toByteArray()

    val encryptedClientMessage = client.encryptMessage(clientMessage)
    val decryptedClientMessage = server.decryptMessage(encryptedClientMessage)
    assert(clientMessage.contentEquals(decryptedClientMessage))

    val encryptedServerMessage = server.encryptMessage(serverMessage)
    val decryptedServerMessage = client.decryptMessage(encryptedServerMessage)
    assert(serverMessage.contentEquals(decryptedServerMessage))
  }

  @Test
  fun testNoiseHardwareBacked() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val se = SecureEnclaveImpl(application)

    val clientKeyPair = se.generateP256KeyPair(
      SeKeySpec(
        name = "key-testNoise-client",
        purposes = SeKeyPurposes.of(AGREEMENT),
        usageConstraints = SeKeyUsageConstraints.NONE,
        validity = null
      )
    )

    val serverKeyPair = se.generateP256KeyPair(
      SeKeySpec(
        name = "key-testNoise-server",
        purposes = SeKeyPurposes.of(AGREEMENT),
        usageConstraints = SeKeyUsageConstraints.NONE,
        validity = null
      )
    )
    val serverPublicKey = se.publicKeyForPrivateKey(serverKeyPair.privateKey)

    val hardwareBackedDhClient = HardwareBackedDhImpl(secureEnclave = se, name = "client")
    val hardwareBackedDhServer = HardwareBackedDhImpl(secureEnclave = se, name = "server")

    val client =
      NoiseContextImpl(
        NoiseRole.INITIATOR,
        CorePrivateKey.HardwareBacked(clientKeyPair.privateKey.name),
        serverPublicKey.bytes,
        hardwareBackedDhClient
      )
    val server =
      NoiseContextImpl(
        NoiseRole.RESPONDER,
        CorePrivateKey.HardwareBacked(serverKeyPair.privateKey.name),
        null,
        hardwareBackedDhServer
      )

    handshakeAndMessage(client, server)
  }

  private fun generateP256Keypair(): KeyPair {
    // Software keypair
    val keyPairGenerator = KeyPairGenerator.getInstance("EC")
    val ecSpec = ECGenParameterSpec("secp256r1")
    keyPairGenerator.initialize(ecSpec)
    return keyPairGenerator.generateKeyPair()
  }

  @Test
  fun testNoiseSoftware() {
    val clientKeyPair = generateP256Keypair()
    val serverKeyPair = generateP256Keypair()

    val serverPublicKey =
      encodePublicKeyAsSEC1Uncompressed((serverKeyPair.public as ECPublicKey))

    val clientPrivateKey = (clientKeyPair.private as ECPrivateKey).s.toByteArray()
    val serverPrivateKey = (serverKeyPair.private as ECPrivateKey).s.toByteArray()

    // Uses the rust implementation for the software backed keypair
    val client = NoiseContextImpl(
      NoiseRole.INITIATOR,
      CorePrivateKey.InMemory(clientPrivateKey),
      serverPublicKey,
      null
    )
    val server = NoiseContextImpl(
      NoiseRole.RESPONDER,
      CorePrivateKey.InMemory(serverPrivateKey),
      null,
      null
    )

    handshakeAndMessage(client, server)
  }
}
