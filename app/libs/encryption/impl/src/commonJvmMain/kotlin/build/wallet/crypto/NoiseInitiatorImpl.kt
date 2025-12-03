package build.wallet.crypto

import bitkey.data.PrivateData
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SymmetricKeyGenerator
import build.wallet.secureenclave.*
import okio.ByteString.Companion.decodeBase64
import build.wallet.rust.core.NoiseRole as CoreNoiseRole
import build.wallet.rust.core.PrivateKey as CorePrivateKey

data class SessionContext(
  val noiseContext: NoiseContextImpl,
)

@OptIn(PrivateData::class)
@BitkeyInject(AppScope::class)
class NoiseInitiatorImpl(
  private val secureEnclave: SecureEnclave,
  private val symmetricKeyGenerator: SymmetricKeyGenerator,
) : NoiseInitiator {
  @Suppress("SwallowedException")
  private fun generatePrivateKey(keyType: NoiseKeyVariant): CorePrivateKey {
    return try {
      // This is a bit kludgy. Here's why:
      // (1) On JVM targets, `SecureEnclaveFake` is injected. It's a fake implementation that doesn't
      //     perform real crypto, which is fine for most tests.
      // (2) JVM E2E integration tests that validate full flows need real crypto. The core library
      //     supports both hardware and software-backed crypto, for testing and for devices without
      //     a secure enclave.
      // (3) The `generateP256KeyPair` function attempts to generate a key pair in the secure enclave.
      //     On real devices without a secure enclave, this will fail. But on the JVM, it will pass.
      //     This is bad because we need real crypto for these tests.
      // (4) So, we explicitly just check if the secure enclave is fake. This will make the underlying
      //     core library fall back to the software implementation, which is what we want.
      if (secureEnclave.isFake()) {
        return CorePrivateKey.InMemory(symmetricKeyGenerator.generate().raw.toByteArray())
      }

      val privateKeyName = "noise-initiator-sk-${keyType.name}"
      secureEnclave.generateP256KeyPair(
        SeKeySpec(
          name = privateKeyName,
          purposes = SeKeyPurposes.of(SeKeyPurpose.AGREEMENT),
          usageConstraints = SeKeyUsageConstraints.NONE,
          validity = null
        )
      )
      CorePrivateKey.HardwareBacked(name = privateKeyName)
    } catch (e: SecureEnclaveError.NoSecureEnclave) {
      // No SE available, maybe it's an emulator or a low-end phone.
      CorePrivateKey.InMemory(symmetricKeyGenerator.generate().raw.toByteArray())
    }
  }

  private val sessionContexts = mutableMapOf<NoiseKeyVariant, SessionContext>()

  private fun sessionContext(keyType: NoiseKeyVariant): SessionContext =
    sessionContexts.getOrPut(keyType) {
      val privateKey = generatePrivateKey(keyType)

      val hardwareBackedDh = if (privateKey is CorePrivateKey.HardwareBacked) {
        HardwareBackedDhImpl(secureEnclave = secureEnclave, name = "app-to-server")
      } else {
        null // Don't use hardware-backed DH if the private key is in memory.
      }

      val noiseContext = NoiseContextImpl(
        role = CoreNoiseRole.INITIATOR,
        privateKey = privateKey,
        theirPublicKey = keyType.serverStaticPubkey.decodeBase64()?.toByteArray(),
        dh = hardwareBackedDh
      )
      SessionContext(
        noiseContext = noiseContext
      )
    }

  override fun initiateHandshake(keyType: NoiseKeyVariant): InitiateHandshakeMessage {
    // Delete the session state if we're re-initiating the handshake.
    sessionContexts.remove(keyType)

    val context = sessionContext(keyType)
    val payload = context.noiseContext.initiateHandshake()

    return InitiateHandshakeMessage(
      payload = payload
    )
  }

  override fun advanceHandshake(
    keyType: NoiseKeyVariant,
    peerHandshakeMessage: ByteArray,
  ) {
    sessionContext(keyType).noiseContext.advanceHandshake(peerHandshakeMessage)
  }

  override fun finalizeHandshake(keyType: NoiseKeyVariant) {
    val context = sessionContext(keyType)
    context.noiseContext.finalizeHandshake()
  }

  override fun encryptMessage(
    keyType: NoiseKeyVariant,
    message: ByteArray,
  ): ByteArray = sessionContext(keyType).noiseContext.encryptMessage(message)

  override fun decryptMessage(
    keyType: NoiseKeyVariant,
    ciphertext: ByteArray,
  ): ByteArray = sessionContext(keyType).noiseContext.decryptMessage(ciphertext)
}
