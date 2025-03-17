package build.wallet.bitcoin.treasury.secrets

import aws.sdk.kotlin.services.secretsmanager.SecretsManagerClient
import aws.sdk.kotlin.services.secretsmanager.model.GetSecretValueRequest
import build.wallet.aws.fromEnvironmentWithTestDefaults
import build.wallet.bitcoin.treasury.secrets.TreasuryWalletSecretsManager.TREASURY_PRIVATE_KEY_ENV_VAR
import build.wallet.bitcoin.treasury.secrets.TreasuryWalletSecretsManager.TREASURY_PRIVATE_KEY_SECRET_ID

actual suspend fun loadTreasuryWalletPrivateKey(): String {
  System.getenv(TREASURY_PRIVATE_KEY_ENV_VAR)?.let {
    return it
  }

  val awsSecretsManagerClient = SecretsManagerClient.fromEnvironmentWithTestDefaults()
  val secretID: String = TREASURY_PRIVATE_KEY_SECRET_ID

  return awsSecretsManagerClient
    .getSecretValue(
      GetSecretValueRequest { secretId = secretID }
    )
    .secretString
    .let {
      requireNotNull(it) { "treasury private key was blank in secrets manager" }
    }
}
