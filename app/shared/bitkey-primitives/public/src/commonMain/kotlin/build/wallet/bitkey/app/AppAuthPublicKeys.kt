package build.wallet.bitkey.app

public data class AppAuthPublicKeys(
  val appGlobalAuthPublicKey: AppGlobalAuthPublicKey,
  val appRecoveryAuthPublicKey: AppRecoveryAuthPublicKey,
)
