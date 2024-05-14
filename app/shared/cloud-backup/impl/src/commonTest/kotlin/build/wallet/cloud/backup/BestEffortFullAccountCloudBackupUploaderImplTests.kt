package build.wallet.cloud.backup

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.cloud.backup.BestEffortFullAccountCloudBackupUploader.Failure.BreakingError
import build.wallet.cloud.backup.BestEffortFullAccountCloudBackupUploader.Failure.IgnorableError
import build.wallet.cloud.backup.CloudBackupError.UnrectifiableCloudBackupError
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError.FullAccountFieldsCreationError
import build.wallet.cloud.backup.local.CloudBackupDaoFake
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudStoreAccountRepositoryMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec

class BestEffortFullAccountCloudBackupUploaderImplTests : FunSpec({

  val cloudBackupDao = CloudBackupDaoFake()
  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
  val fullAccountCloudBackupCreator = FullAccountCloudBackupCreatorMock(turbines::create)
  val cloudBackupRepository = CloudBackupRepositoryFake()
  val cloudAccount = CloudAccountMock("cloudInstanceId")

  val uploader = BestEffortFullAccountCloudBackupUploaderImpl(
    cloudBackupDao = cloudBackupDao,
    cloudStoreAccountRepository = cloudStoreAccountRepository,
    fullAccountCloudBackupCreator = fullAccountCloudBackupCreator,
    cloudBackupRepository = cloudBackupRepository
  )

  beforeEach {
    cloudBackupDao.reset()
    cloudStoreAccountRepository.reset()
    fullAccountCloudBackupCreator.reset()
    cloudBackupRepository.reset()
  }

  test("success - cloud backup v2") {
    cloudBackupDao.set(
      FullAccountMock.accountId.serverId,
      CloudBackupV2WithFullAccountMock
        // Changing one arbitrary thing about this so it's different from the one being written.
        .copy(isUsingSocRecFakes = true)
    )
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
    fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)
    uploader.createAndUploadCloudBackup(FullAccountMock)
      .shouldBeOk()
    fullAccountCloudBackupCreator.createCalls.awaitItem()
    cloudBackupDao.get(FullAccountMock.accountId.serverId)
      .shouldBeOk(CloudBackupV2WithFullAccountMock)
    cloudBackupRepository.readBackup(cloudAccount).shouldBeOk(CloudBackupV2WithFullAccountMock)
  }

  test("failure - lite account cloud backup") {
    cloudBackupDao.set(FullAccountMock.accountId.serverId, CloudBackupV2WithLiteAccountMock)
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
    uploader.createAndUploadCloudBackup(FullAccountMock)
      .shouldBeErrOfType<BreakingError>()
    cloudBackupRepository.readBackup(cloudAccount).shouldBeOk(null)
  }

  test("failure - no cloud backup set") {
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
    fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)
    uploader.createAndUploadCloudBackup(FullAccountMock)
      .shouldBeErrOfType<IgnorableError>()
    cloudBackupRepository.readBackup(cloudAccount).shouldBeOk(null)
  }

  test("failure - no cloud account") {
    cloudStoreAccountRepository.currentAccountResult = Ok(null)
    uploader.createAndUploadCloudBackup(FullAccountMock)
      .shouldBeErrOfType<IgnorableError>()
    cloudBackupRepository.readBackup(cloudAccount).shouldBeOk(null)
  }

  test("failure - failed to create cloud backup") {
    cloudBackupDao.set(FullAccountMock.accountId.serverId, CloudBackupV2WithFullAccountMock)
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
    fullAccountCloudBackupCreator.backupResult = Err(FullAccountFieldsCreationError())
    uploader.createAndUploadCloudBackup(FullAccountMock)
      .shouldBeErrOfType<BreakingError>()
    fullAccountCloudBackupCreator.createCalls.awaitItem()
    cloudBackupRepository.readBackup(cloudAccount).shouldBeOk(null)
  }

  test("failure - failed to upload cloud backup") {
    cloudBackupDao.set(FullAccountMock.accountId.serverId, CloudBackupV2WithFullAccountMock)
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
    fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)
    cloudBackupRepository.returnWriteError = UnrectifiableCloudBackupError(Throwable())
    uploader.createAndUploadCloudBackup(FullAccountMock)
      .shouldBeErrOfType<IgnorableError>()
    fullAccountCloudBackupCreator.createCalls.awaitItem()
    cloudBackupRepository.readBackup(cloudAccount).shouldBeOk(null)
  }
})
