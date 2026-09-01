package build.wallet.cloud.backup.migration

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.cloud.store.iCloudAccount
import build.wallet.store.KeyValueStoreFactoryFake
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class CloudKitBackupMigrationStatusDaoImplTests : FunSpec({
  val keyValueStoreFactory = KeyValueStoreFactoryFake()
  val dao = CloudKitBackupMigrationStatusDaoImpl(keyValueStoreFactory)

  val iCloudStoreAccount = iCloudAccount(ubiquityIdentityToken = "test-ubiquity-identity-token")
  val otherICloudStoreAccount = iCloudAccount(ubiquityIdentityToken = "other-ubiquity-identity-token")

  beforeTest {
    keyValueStoreFactory.clear()
  }

  test("reconciled marker round trips") {
    dao.isReconciled(FullAccountMock.accountId, iCloudStoreAccount).shouldBeOk(false)

    dao.setReconciled(FullAccountMock.accountId, iCloudStoreAccount).shouldBeOk()

    dao.isReconciled(FullAccountMock.accountId, iCloudStoreAccount).shouldBeOk(true)
  }

  test("reconciled markers are scoped per account and iCloud identity") {
    dao.setReconciled(FullAccountMock.accountId, iCloudStoreAccount).shouldBeOk()

    dao.isReconciled(LiteAccountMock.accountId, iCloudStoreAccount).shouldBeOk(false)
    dao.isReconciled(FullAccountMock.accountId, otherICloudStoreAccount).shouldBeOk(false)
  }

  test("clear removes reconciliation markers") {
    dao.setReconciled(FullAccountMock.accountId, iCloudStoreAccount).shouldBeOk()
    dao.setReconciled(LiteAccountMock.accountId, otherICloudStoreAccount).shouldBeOk()

    dao.clear().shouldBeOk()

    dao.isReconciled(FullAccountMock.accountId, iCloudStoreAccount).shouldBeOk(false)
    dao.isReconciled(LiteAccountMock.accountId, otherICloudStoreAccount).shouldBeOk(false)
  }

  // Markers persisted by the original implementation use the same "active-reconciled:" key
  // format, so the prefix filter in clear() must match them. Guards against a future key
  // format change silently orphaning markers, which would suppress reconciliation when the
  // CloudKit flag is re-enabled.
  test("clear removes markers persisted in the original key format") {
    val legacyStore = keyValueStoreFactory.getOrCreate("CloudKitBackupMigrationStatus")
    val legacyKey = "active-reconciled:" +
      "${FullAccountMock.accountId.serverId}:${iCloudStoreAccount.ubiquityIdentityToken}"
        .encodeUtf8().sha256().hex()
    legacyStore.putString(legacyKey, "true")

    dao.isReconciled(FullAccountMock.accountId, iCloudStoreAccount).shouldBeOk(true)

    dao.clear().shouldBeOk()

    legacyStore.getStringOrNull(legacyKey).shouldBe(null)
    dao.isReconciled(FullAccountMock.accountId, iCloudStoreAccount).shouldBeOk(false)
  }

  // Regression test for the theme-preference wipe: on iOS every store created by
  // KeyValueStoreFactory shares NSUserDefaults.standardUserDefaults, so calling
  // SuspendSettings.clear() here erased unrelated app state. clear() must only remove
  // keys owned by this DAO.
  test("clear does not remove keys owned by other stores") {
    val sharedStore = keyValueStoreFactory.getOrCreate("THEME_PREFERENCE_STORE")
    sharedStore.putString("theme_preference", "MANUAL_LIGHT")
    sharedStore.putString("unrelated_key", "unrelated_value")
    dao.setReconciled(FullAccountMock.accountId, iCloudStoreAccount).shouldBeOk()

    dao.clear().shouldBeOk()

    sharedStore.getStringOrNull("theme_preference").shouldBe("MANUAL_LIGHT")
    sharedStore.getStringOrNull("unrelated_key").shouldBe("unrelated_value")
    dao.isReconciled(FullAccountMock.accountId, iCloudStoreAccount).shouldBeOk(false)
  }
})
