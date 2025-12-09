package build.wallet.onboarding

import bitkey.onboarding.UpgradeLiteAccountToFullService
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.KeyCrossDraft
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.backup.LiteAccountCloudBackupRestorer
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.keybox.keys.OnboardingAppKeyKeystore
import build.wallet.onboarding.LiteAccountBackupToFullAccountUpgrader.UpgradeError
import build.wallet.platform.random.UuidGenerator
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIfNull
import com.github.michaelbull.result.toResultOr

@BitkeyInject(AppScope::class)
class LiteAccountBackupToFullAccountUpgraderImpl(
  private val liteAccountCloudBackupRestorer: LiteAccountCloudBackupRestorer,
  private val onboardingAppKeyKeystore: OnboardingAppKeyKeystore,
  private val onboardingKeyboxHardwareKeysDao: OnboardingKeyboxHardwareKeysDao,
  private val uuidGenerator: UuidGenerator,
  private val upgradeLiteAccountToFullService: UpgradeLiteAccountToFullService,
) : LiteAccountBackupToFullAccountUpgrader {
  override suspend fun upgradeAccount(
    cloudBackup: CloudBackup,
    onboardingKeybox: Keybox,
  ): Result<FullAccount, UpgradeError> =
    coroutineBinding {
      require(cloudBackup is CloudBackupV2 || cloudBackup is CloudBackupV3) {
        "Unsupported cloud backup version"
      }

      val liteAccount =
        liteAccountCloudBackupRestorer.restoreFromBackup(cloudBackup)
          .mapError { UpgradeError("Failed to restore from backup", it) }
          .bind()

      val appKeyBundle =
        onboardingAppKeyKeystore.getAppKeyBundle(
          uuidGenerator.random(),
          liteAccount.config.bitcoinNetworkType
        ).toResultOr { UpgradeError("Missing onboarding app key bundle") }.bind()

      val hwKeys =
        onboardingKeyboxHardwareKeysDao.get()
          .mapError { UpgradeError("Failed to get onboarding keybox hw auth public key", it) }
          .toErrorIfNull { UpgradeError("Missing onboarding keybox hw auth public key") }
          .bind()

      val keyCross =
        KeyCrossDraft.WithAppKeysAndHardwareKeys(
          appKeyBundle = appKeyBundle,
          hardwareKeyBundle = HwKeyBundle(
            localId = uuidGenerator.random(),
            spendingKey = onboardingKeybox.activeSpendingKeyset.hardwareKey,
            authKey = hwKeys.hwAuthPublicKey,
            networkType = liteAccount.config.bitcoinNetworkType
          ),
          appGlobalAuthKeyHwSignature = hwKeys.appGlobalAuthKeyHwSignature,
          config = onboardingKeybox.config
        )

      upgradeLiteAccountToFullService.upgradeAccount(liteAccount, keyCross)
        .mapError { UpgradeError("Failed to upgrade lite account", it) }
        .bind()
    }
}
