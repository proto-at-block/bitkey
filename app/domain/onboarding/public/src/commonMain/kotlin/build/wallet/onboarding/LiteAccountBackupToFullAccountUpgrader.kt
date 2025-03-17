package build.wallet.onboarding

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.CloudBackup
import com.github.michaelbull.result.Result

/**
 * Restores a Lite Account backup, and upgrades it to an onboarding Full Account using the given
 * keybox.
 */
interface LiteAccountBackupToFullAccountUpgrader {
  suspend fun upgradeAccount(
    cloudBackup: CloudBackup,
    onboardingKeybox: Keybox,
  ): Result<FullAccount, UpgradeError>

  data class UpgradeError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : Error(message, cause)
}
