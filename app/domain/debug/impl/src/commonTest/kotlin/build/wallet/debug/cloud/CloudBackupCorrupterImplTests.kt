package build.wallet.debug.cloud

import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.cloud.backup.CloudBackupStoreFake
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.backup.CloudBackupV3WithFullAccountMock
import build.wallet.cloud.backup.local.CloudBackupDaoFake
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountRepositoryMock
import build.wallet.platform.config.AppVariant.Development
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

class CloudBackupCorrupterImplTests : FunSpec({
  val cloudBackupStore = CloudBackupStoreFake()
  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
  val cloudBackupStoreKeys = CloudBackupStoreKeysFake()
  val cloudBackupDao = CloudBackupDaoFake()
  val cloudAccount = CloudAccountMock("")

  fun cloudBackupCorrupter() =
    CloudBackupCorrupterImpl(
      appVariant = Development,
      cloudBackupStore = cloudBackupStore,
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      cloudBackupStoreKeys = cloudBackupStoreKeys,
      cloudBackupDao = cloudBackupDao
    )

  beforeTest {
    cloudBackupStore.reset()
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
    cloudBackupDao.reset()
  }

  test("corrupts only the active account backup") {
    val activeBackup = CloudBackupV3WithFullAccountMock
    val otherBackup = CloudBackupV3WithFullAccountMock.copy(
      accountId = "other-account-id",
      deviceNickname = "Other device"
    )
    val activeBackupKey = cloudBackupStoreKeys.activeBackupFormatAccountSpecificKey(FullAccountIdMock)
    cloudBackupStore.setBackup(cloudAccount, activeBackupKey, activeBackup)
    cloudBackupStore.setBackup(cloudAccount, "cb-other-account-id", otherBackup)

    val corrupter = cloudBackupCorrupter()

    corrupter.corrupt(FullAccountIdMock).shouldBeOk()

    val corruptedActiveBackup = cloudBackupStore.getBackup(cloudAccount, activeBackupKey)
      .shouldNotBeNull()
    corruptedActiveBackup.accountId.shouldBe(activeBackup.accountId)
    corruptedActiveBackup.fullAccountFields
      ?.hwFullAccountKeysCiphertext
      .shouldBe(corrupter.sealedDataMock)

    cloudBackupStore.getBackup(cloudAccount, "cb-other-account-id").shouldBe(otherBackup)
    cloudBackupDao.get(activeBackup.accountId).shouldBeOk().shouldBe(corruptedActiveBackup)
    cloudBackupDao.get(otherBackup.accountId).shouldBeOk().shouldBeNull()
  }

  test("corrupts legacy active account backup") {
    val activeBackup = CloudBackupV3WithFullAccountMock
    cloudBackupStore.setBackup(cloudAccount, "cloud-backup", activeBackup)

    val corrupter = cloudBackupCorrupter()

    corrupter.corrupt(FullAccountIdMock).shouldBeOk()

    val corruptedActiveBackup = cloudBackupStore.getBackup(cloudAccount, "cloud-backup")
      .shouldNotBeNull()
    corruptedActiveBackup.fullAccountFields
      ?.hwFullAccountKeysCiphertext
      .shouldBe(corrupter.sealedDataMock)
    cloudBackupDao.get(activeBackup.accountId).shouldBeOk().shouldBe(corruptedActiveBackup)
  }

  test("does not deserialize unrelated malformed account-specific backups") {
    val activeBackup = CloudBackupV3WithFullAccountMock
    val activeBackupKey = cloudBackupStoreKeys.activeBackupFormatAccountSpecificKey(FullAccountIdMock)
    cloudBackupStore.setBackup(cloudAccount, activeBackupKey, activeBackup)
    cloudBackupStore.set(
      account = cloudAccount,
      key = "cb-other-account-id",
      value = "not-json".encodeUtf8()
    ).shouldBeOk()

    val corrupter = cloudBackupCorrupter()

    corrupter.corrupt(FullAccountIdMock).shouldBeOk()

    val corruptedActiveBackup = cloudBackupStore.getBackup(cloudAccount, activeBackupKey)
      .shouldNotBeNull()
    corruptedActiveBackup.fullAccountFields
      ?.hwFullAccountKeysCiphertext
      .shouldBe(corrupter.sealedDataMock)
    cloudBackupStore.get(cloudAccount, "cb-other-account-id")
      .shouldBeOk()
      ?.utf8()
      .shouldBe("not-json")
  }

  test("fails when no cloud backup exists for the active account") {
    val otherBackup = CloudBackupV3WithFullAccountMock.copy(accountId = "other-account-id")
    cloudBackupStore.setBackup(cloudAccount, "cb-other-account-id", otherBackup)

    cloudBackupCorrupter().corrupt(FullAccountIdMock)
      .shouldBeErrOfType<CorruptionError.BackupNotFoundError>()
      .message.shouldBe("No cloud backup found for active account.")

    cloudBackupStore.getBackup(cloudAccount, "cb-other-account-id").shouldBe(otherBackup)
    cloudBackupDao.get(otherBackup.accountId).shouldBeOk().shouldBeNull()
  }
})

private suspend fun CloudBackupStoreFake.setBackup(
  account: CloudStoreAccount,
  key: String,
  backup: CloudBackupV3,
) {
  set(
    account = account,
    key = key,
    value = Json.encodeToString(CloudBackupV3.serializer(), backup).encodeUtf8()
  ).shouldBeOk()
}

private suspend fun CloudBackupStoreFake.getBackup(
  account: CloudStoreAccount,
  key: String,
): CloudBackupV3? =
  get(account, key)
    .shouldBeOk()
    ?.utf8()
    ?.let { Json.decodeFromString<CloudBackupV3>(it) }
