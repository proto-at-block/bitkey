package build.wallet.cloud.backup.migration

import build.wallet.cloud.backup.CloudBackupOperationLockImpl
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.IosCloudKitBackupFeatureFlag
import build.wallet.platform.config.AppVariant
import build.wallet.worker.BackgroundStrategy
import build.wallet.worker.RunStrategy
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CloudKitBackupMigrationWorkerImplTests : FunSpec({
  val featureFlagDao = FeatureFlagDaoFake()
  val featureFlag = IosCloudKitBackupFeatureFlag(featureFlagDao)
  val lock = CloudBackupOperationLockImpl()
  val service = CloudKitBackupMigrationServiceFake()
  val cloudKitBackupMigrationStatusDao = CloudKitBackupMigrationStatusDaoFake()

  fun worker(appVariant: AppVariant = AppVariant.Customer) =
    CloudKitBackupMigrationWorkerImpl(
      appVariant = appVariant,
      iosCloudKitBackupFeatureFlag = featureFlag,
      cloudBackupOperationLock = lock,
      cloudKitBackupMigrationService = service,
      cloudKitBackupMigrationStatusDao = cloudKitBackupMigrationStatusDao
    )

  beforeTest {
    featureFlagDao.reset()
    service.reset()
    cloudKitBackupMigrationStatusDao.reset()
  }

  test("run strategy is startup and flag updates") {
    val runStrategy = worker().runStrategy

    runStrategy.filterIsInstance<RunStrategy.Startup>().single()
      .backgroundStrategy
      .shouldBe(BackgroundStrategy.Wait)
    runStrategy.filterIsInstance<RunStrategy.OnEvent>().single()
      .backgroundStrategy
      .shouldBe(BackgroundStrategy.Wait)
  }

  test("skips when flag is off") {
    featureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))

    worker().executeWork()

    service.migrateIfNeededCallCount.shouldBe(0)
    cloudKitBackupMigrationStatusDao.clearCallCount.shouldBe(1)
  }

  test("runs migration when flag is on") {
    featureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    worker().executeWork()

    service.migrateIfNeededCallCount.shouldBe(1)
  }

  test("skips in Emergency (EEK) variant") {
    featureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    worker(AppVariant.Emergency).executeWork()

    service.migrateIfNeededCallCount.shouldBe(0)
  }

  test("runs in Customer variant") {
    featureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    worker(AppVariant.Customer).executeWork()

    service.migrateIfNeededCallCount.shouldBe(1)
  }

  test("runs in Development variant") {
    featureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    worker(AppVariant.Development).executeWork()

    service.migrateIfNeededCallCount.shouldBe(1)
  }
})
