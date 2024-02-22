package build.wallet.encrypt

data class Secp256k1Keypair(
  val publicKey: Secp256k1PublicKey,
  val privateKey: Secp256k1PrivateKey,
)
