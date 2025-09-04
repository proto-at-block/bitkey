package build.wallet.cloud.backup

import bitkey.auth.AuthTokenScope.Recovery
import build.wallet.auth.AccountAuthTokensMock
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.cloud.backup.CloudBackupError.UnrectifiableCloudBackupError
import build.wallet.cloud.backup.local.BackupStorageError
import build.wallet.cloud.backup.local.CloudBackupDaoFake
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudKeyValueStoreFake
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CloudBackupRepositoryImplTests : FunSpec({
  val accountId = FullAccountId("foo")
  val cloudAccount = CloudAccountMock(instanceId = "jack")
  val cloudKeyValueStore = CloudKeyValueStoreFake()
  val cloudBackupDao = CloudBackupDaoFake()
  val authTokensService = AuthTokensServiceFake()

  val cloudBackupRepository = CloudBackupRepositoryImpl(
    cloudKeyValueStore = cloudKeyValueStore,
    cloudBackupDao = cloudBackupDao,
    authTokensService = authTokensService,
    jsonSerializer = JsonSerializer()
  )

  afterTest {
    cloudBackupDao.reset()
    cloudKeyValueStore.reset()
    authTokensService.reset()
  }

  backupTestData.forEach {
    val backup = it.backup
    val backupJson = it.json

    context(it.testName) {
      test("write backup to cloud key-value store and dao") {
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupRepository.writeBackup(accountId, cloudAccount, backup, true).shouldBeOk()

        cloudBackupDao.get(accountId.serverId).shouldBeOk(backup)
        cloudKeyValueStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(backupJson)
      }

      test("write backup - dao error") {
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudBackupDao.returnError = true

        cloudBackupRepository.writeBackup(accountId, cloudAccount, backup, true)
          .shouldBeErr(UnrectifiableCloudBackupError(BackupStorageError()))

        cloudKeyValueStore.getString(cloudAccount, key = "cloud-backup").shouldBeOk(backupJson)
        cloudBackupDao.get(accountId.serverId).shouldBeErr(BackupStorageError())
      }

      test("write backup - cloud key-value error") {
        authTokensService.setTokens(accountId, AccountAuthTokensMock, Recovery)
        cloudKeyValueStore.returnError = true

        cloudBackupRepository.writeBackup(accountId, cloudAccount, backup, true)
          .shouldBeErr(UnrectifiableCloudBackupError(CloudError()))

        cloudKeyValueStore.getString(cloudAccount, key = "cloud-backup")
          .shouldBeErr(CloudError())
        // Backup was not written to local storage because we failed to write it to cloud store
        cloudBackupDao.get(accountId.serverId).shouldBeOk(null)
      }

      test("write backup - error authenticating") {
        val error = Error("foo")
        authTokensService.refreshAccessTokenError = error

        cloudBackupRepository.writeBackup(accountId, cloudAccount, backup, true)
          .shouldBeErr(UnrectifiableCloudBackupError(error))

        // Backup was not written to local storage because we failed to write it to cloud store
        cloudBackupDao.get(accountId.serverId).shouldBeOk(null)
      }

      test("backup exists in cloud-key value store") {
        cloudKeyValueStore.setString(cloudAccount, key = "cloud-backup", value = backupJson)

        cloudBackupRepository.readBackup(cloudAccount).shouldBeOk(backup)
      }
    }
  }
})

private data class BackupTestData(
  val testName: String,
  val backup: CloudBackup,
  /** JSON representation of [backup] instance. */
  val json: String,
)

private val backupV2 = CloudBackupV2WithFullAccountMock
private val backupV2Json = Json.encodeToString(backupV2)

private val backupTestData =
  listOf(
    BackupTestData("backup v2", backupV2, backupV2Json)
  )
