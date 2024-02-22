package build.wallet.bitkey.app

/**
 * App authentication keypair (public and private keys) with "recovery" authentication scope.
 * Mostly used to authorize Social Recovery operations.
 */
data class AppRecoveryAuthKeypair(
  override val publicKey: AppRecoveryAuthPublicKey,
  override val privateKey: AppRecoveryAuthPrivateKey,
) : AppAuthKeypair
