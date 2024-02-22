package build.wallet.encrypt

import okio.ByteString

interface Secp256k1SharedSecret {
  fun deriveSharedSecret(
    privateKey: Secp256k1PrivateKey,
    publicKey: Secp256k1PublicKey,
  ): ByteString
}
