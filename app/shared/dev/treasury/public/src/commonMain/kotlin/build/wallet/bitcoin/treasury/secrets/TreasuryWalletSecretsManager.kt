package build.wallet.bitcoin.treasury.secrets

internal object TreasuryWalletSecretsManager {
  const val TREASURY_PRIVATE_KEY_SECRET_ID = "ops/e2e_signet_key"
  const val TREASURY_PRIVATE_KEY_ENV_VAR = "TEST_TREASURY_PRIVATE_KEY"
}
