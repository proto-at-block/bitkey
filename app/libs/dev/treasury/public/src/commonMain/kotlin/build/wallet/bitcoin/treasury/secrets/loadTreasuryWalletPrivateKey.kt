package build.wallet.bitcoin.treasury.secrets

/**
 * Loads the private key for treasury spending wallet.
 * Either from system environment variable if available, or from AWS Secrets Manager.
 */
expect suspend fun loadTreasuryWalletPrivateKey(): String
