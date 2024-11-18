package build.wallet.crypto

import build.wallet.secureenclave.*
import build.wallet.secureenclave.SeKeyPurpose.AGREEMENT
import build.wallet.rust.core.HardwareBackedDh as CoreHardwareBackedDh
import build.wallet.rust.core.HardwareBackedKeyPair as CoreHardwareBackedKeyPair

// This is ONLY intended for Noise.
class HardwareBackedDhImpl(
  val secureEnclave: SecureEnclave,
  val name: String, // Identifies the context for which this DH impl is used; e.g. app-firmware, app-server, etc.
) : CoreHardwareBackedDh {
  override fun dh(
    ourPrivkeyName: String,
    peerPubkey: ByteArray,
  ): ByteArray = secureEnclave.diffieHellman(SeKeyHandle(ourPrivkeyName), SePublicKey(peerPubkey))

  override fun generate(): CoreHardwareBackedKeyPair {
    val seKeyPair = secureEnclave.generateP256KeyPair(
      SeKeySpec(
        name = "bitkey-noise-ephemeral-key-$name",
        purposes = SeKeyPurposes.of(AGREEMENT),
        usageConstraints = SeKeyUsageConstraints.NONE, // No biometrics or PIN required
        validity = null
      )
    )

    return CoreHardwareBackedKeyPair(
      privkeyName = seKeyPair.privateKey.name,
      pubkey = seKeyPair.publicKey.bytes
    )
  }

  override fun pubkey(privKeyName: String): ByteArray =
    secureEnclave
      .publicKeyForPrivateKey(SeKeyHandle(privKeyName))
      .bytes
}
