package build.wallet.crypto

import build.wallet.rust.core.HardwareBackedDh as CoreHardwareBackedDh
import build.wallet.rust.core.NoiseContext as CoreNoiseContext
import build.wallet.rust.core.NoiseContextInterface as CoreNoiseContextInterface
import build.wallet.rust.core.NoiseRole as CoreNoiseRole
import build.wallet.rust.core.PrivateKey as CorePrivateKey

class NoiseContextImpl(
  role: CoreNoiseRole,
  privateKey: CorePrivateKey,
  theirPublicKey: ByteArray?, // SEC1 uncompressed public key: 0x04 || x || y
  dh: CoreHardwareBackedDh?,
) : CoreNoiseContextInterface {
  private val coreContext: CoreNoiseContext

  init {
    coreContext = CoreNoiseContext(
      role,
      privateKey,
      theirPublicKey,
      dh
    )
  }

  override fun initiateHandshake(): ByteArray = coreContext.initiateHandshake()

  override fun advanceHandshake(peerHandshakeMessage: ByteArray): ByteArray? =
    coreContext
      .advanceHandshake(peerHandshakeMessage)

  override fun finalizeHandshake() {
    coreContext.finalizeHandshake()
  }

  override fun encryptMessage(message: ByteArray): ByteArray = coreContext.encryptMessage(message)

  override fun decryptMessage(ciphertext: ByteArray): ByteArray =
    coreContext
      .decryptMessage(ciphertext)
}
