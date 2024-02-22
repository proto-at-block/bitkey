package build.wallet.bitkey.auth

interface AuthKeypair {
  val publicKey: AuthPublicKey
  val privateKey: AuthPrivateKey
}
