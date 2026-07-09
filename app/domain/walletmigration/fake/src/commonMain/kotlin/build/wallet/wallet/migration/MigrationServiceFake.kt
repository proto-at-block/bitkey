package build.wallet.wallet.migration

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.money.BitcoinMoney
import build.wallet.wallet.migration.MigrationError
import build.wallet.wallet.migration.MigrationProgress
import build.wallet.wallet.migration.MigrationService
import build.wallet.wallet.migration.MigrationType
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onSuccess

class MigrationServiceFake : MigrationService {
  var resumeResult: Result<MigrationProgress, MigrationError> =
    Ok(MigrationProgress.NotStarted(MigrationType.PrivateWalletMigration))

  /**
   * Default result for proceed() when proceedResults queue is empty.
   */
  var proceedResult: Result<MigrationProgress, MigrationError> =
    Ok(MigrationProgress.Completed(MigrationType.PrivateWalletMigration))

  /**
   * Queue of results for proceed(). If non-empty, these are returned in order.
   * When the queue is exhausted, [proceedResult] is used.
   */
  var proceedResults = mutableListOf<Result<MigrationProgress, MigrationError>>()

  var estimateMigrationFeesResult: Result<BitcoinMoney, MigrationError> =
    Ok(BitcoinMoney.sats(1000))

  /**
   * Queue of results for estimateMigrationFees(). If non-empty, these are returned in order.
   * When the queue is exhausted, [estimateMigrationFeesResult] is used.
   */
  var estimateMigrationFeesResults = mutableListOf<Result<BitcoinMoney, MigrationError>>()

  var resumeCalls = mutableListOf<MigrationType>()
  var proceedCalls = mutableListOf<MigrationProgress>()
  var hardwareAuthKeyAvailabilityResult: Result<HardwareAuthKeyAvailabilityStatus, MigrationError> =
    Ok(HardwareAuthKeyAvailabilityStatus.Available)
  var hardwareAuthKeyAvailabilityCalls = mutableListOf<Pair<FullAccount, HwAuthPublicKey>>()
  var clearMigrationCalls = mutableListOf<MigrationType>()
  var estimateMigrationFeesCalls = mutableListOf<Pair<FullAccount, String?>>()
  var isW3UpgradeInProgressResult: Boolean = false
  var isW3UpgradeInProgressCalls = 0

  override suspend fun resume(type: MigrationType): Result<MigrationProgress, MigrationError> {
    resumeCalls.add(type)
    return resumeResult
  }

  override suspend fun proceed(
    state: MigrationProgress,
  ): Result<MigrationProgress, MigrationError> {
    proceedCalls.add(state)
    val result = if (proceedResults.isNotEmpty()) {
      proceedResults.removeFirst()
    } else {
      proceedResult
    }
    result.onSuccess {
      if (state is MigrationProgress.CreateNewKeyset.W3Upgrade) {
        savedOldHardwareFingerprint = state.oldHardwareFingerprint
      }
    }
    return result
  }

  override suspend fun estimateMigrationFees(
    account: FullAccount,
    oldHardwareFingerprint: String?,
  ): Result<BitcoinMoney, MigrationError> {
    estimateMigrationFeesCalls.add(account to oldHardwareFingerprint)
    return if (estimateMigrationFeesResults.isNotEmpty()) {
      estimateMigrationFeesResults.removeFirst()
    } else {
      estimateMigrationFeesResult
    }
  }

  override suspend fun checkW3UpgradeHardwareAuthKeyAvailability(
    account: FullAccount,
    hwAuthPublicKey: HwAuthPublicKey,
  ): Result<HardwareAuthKeyAvailabilityStatus, MigrationError> {
    hardwareAuthKeyAvailabilityCalls += account to hwAuthPublicKey
    return hardwareAuthKeyAvailabilityResult
  }

  override suspend fun clearMigration(type: MigrationType) {
    clearMigrationCalls.add(type)
  }

  override suspend fun isW3UpgradeInProgress(
    f8eEnvironment: F8eEnvironment,
    hwAuthPublicKey: HwAuthPublicKey,
  ): Boolean {
    isW3UpgradeInProgressCalls += 1
    return isW3UpgradeInProgressResult
  }

  var savedOldHardwareFingerprint: String? = null

  override suspend fun getOldHardwareFingerprint(): Result<String?, MigrationError> {
    return Ok(savedOldHardwareFingerprint)
  }

  fun reset() {
    resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.PrivateWalletMigration))
    proceedResult = Ok(MigrationProgress.Completed(MigrationType.PrivateWalletMigration))
    proceedResults.clear()
    estimateMigrationFeesResult = Ok(BitcoinMoney.sats(1000))
    estimateMigrationFeesResults.clear()
    resumeCalls.clear()
    proceedCalls.clear()
    hardwareAuthKeyAvailabilityResult = Ok(HardwareAuthKeyAvailabilityStatus.Available)
    hardwareAuthKeyAvailabilityCalls.clear()
    clearMigrationCalls.clear()
    estimateMigrationFeesCalls.clear()
    isW3UpgradeInProgressResult = false
    isW3UpgradeInProgressCalls = 0
    savedOldHardwareFingerprint = null
  }
}
