package build.wallet.crypto

/**
 * A handle to an elliptic curve asymmetric key. The underlying class
 * optionally contains private key material.
 */
interface AsymmetricKey<K : KeyPurpose> : Key {
  val publicKey: PublicKey<K>
}
