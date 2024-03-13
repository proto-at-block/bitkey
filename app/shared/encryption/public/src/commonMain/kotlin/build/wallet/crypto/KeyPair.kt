package build.wallet.crypto

data class KeyPair(
  val publicKey: PublicKey,
  val privateKey: PrivateKey,
  val curveType: CurveType,
)
