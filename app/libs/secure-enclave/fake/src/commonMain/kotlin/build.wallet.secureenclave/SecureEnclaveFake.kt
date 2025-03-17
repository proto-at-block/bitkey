package build.wallet.secureenclave

import kotlin.random.Random

class SecureEnclaveFake : SecureEnclave {
  private val keyMap = mutableMapOf<String, ByteArray>()

  override fun generateP256KeyPair(spec: SeKeySpec): SeKeyPair {
    val privateKeyBytes = generateFakePrivateKey()
    keyMap[spec.name] = privateKeyBytes

    return SeKeyPair(
      privateKey = SeKeyHandle(name = spec.name),
      publicKey = SePublicKey(bytes = generateFakePublicKey(privateKeyBytes))
    )
  }

  override fun publicKeyForPrivateKey(sePrivateKey: SeKeyHandle): SePublicKey {
    val privateKeyBytes = keyMap[sePrivateKey.name]
      ?: throw IllegalArgumentException("Private key not found")

    return SePublicKey(bytes = generateFakePublicKey(privateKeyBytes))
  }

  override fun diffieHellman(
    ourPrivateKey: SeKeyHandle,
    peerPublicKey: SePublicKey,
  ): ByteArray = ourPrivateKey.name.encodeToByteArray() + peerPublicKey.bytes

  override fun loadKeyPair(name: String): SeKeyPair {
    val privateKeyBytes = keyMap[name]
      ?: throw IllegalArgumentException("Key pair not found")

    return SeKeyPair(
      privateKey = SeKeyHandle(name = name),
      publicKey = SePublicKey(bytes = generateFakePublicKey(privateKeyBytes))
    )
  }

  override fun isFake(): Boolean = true

  private fun generateFakePrivateKey(): ByteArray = Random.nextBytes(32)

  private fun generateFakePublicKey(privateKeyBytes: ByteArray): ByteArray {
    return privateKeyBytes.reversedArray() // Just reverse for a simple, unique "public key"
  }
}
