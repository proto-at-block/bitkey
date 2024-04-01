package build.wallet.crypto

interface KeyPair<T : KeyPurpose> : AsymmetricKey<T> {
  override val publicKey: PublicKey<T>
  val privateKey: PrivateKey<T>
}
