package build.wallet.bitkey.app

/**
 * App authentication keypair (public and private keys) with "global"/"normal" scope.
 */
data class AppGlobalAuthKeypair(
  override val publicKey: AppGlobalAuthPublicKey,
  override val privateKey: AppGlobalAuthPrivateKey,
) : AppAuthKeypair
