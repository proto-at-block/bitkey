package build.wallet.onboarding

import app.cash.turbine.Turbine
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class LiteAccountBackupToFullAccountUpgraderMock(
  turbine: (String) -> Turbine<Pair<CloudBackup, Keybox>>,
) : LiteAccountBackupToFullAccountUpgrader {
  val upgradeAccountCalls = turbine("upgradeAccount calls")

  val defaultResult = Ok(FullAccountMock)
  var result: Result<FullAccount, LiteAccountBackupToFullAccountUpgrader.UpgradeError> = defaultResult

  override suspend fun upgradeAccount(
    cloudBackup: CloudBackup,
    onboardingKeybox: Keybox,
  ): Result<FullAccount, LiteAccountBackupToFullAccountUpgrader.UpgradeError> {
    require(cloudBackup is CloudBackupV2 || cloudBackup is CloudBackupV3) {
      "Unsupported cloud backup version"
    }
    upgradeAccountCalls.add(cloudBackup to onboardingKeybox)
    return result
  }

  fun reset() {
    result = defaultResult
  }
}
