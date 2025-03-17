package build.wallet.bitkey.spending

/**
 * A keypair meant to be used for spending funds.
 */
data class SpendingKeypair(
  val publicKey: SpendingPublicKey,
  val privateKey: SpendingPrivateKey,
)
