package build.wallet.debug.cloud

import bitkey.account.AccountConfigServiceFake
import build.wallet.cloud.backup.CloudBackupStoreFake
import build.wallet.cloud.store.CloudKitKeyValueStoreFake
import build.wallet.cloud.store.CloudStoreAccountRepositoryMock
import build.wallet.cloud.store.UbiquitousKeyValueStoreFake
import build.wallet.cloud.store.iCloudAccount
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.IosCloudKitBackupFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.testing.shouldBeOk
import build.wallet.testing.shouldBeOkOfType
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class CloudBackupViewerImplTests : FunSpec({
  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
  val ubiquitousKeyValueStore = UbiquitousKeyValueStoreFake()
  val cloudKitKeyValueStore = CloudKitKeyValueStoreFake()
  val cloudBackupStore = CloudBackupStoreFake()
  val cloudBackupStoreKeys = CloudBackupStoreKeysFake()
  val accountConfigService = AccountConfigServiceFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val iosCloudKitBackupFeatureFlag = IosCloudKitBackupFeatureFlag(featureFlagDao)
  val cloudBackupViewer =
    CloudBackupViewerImpl(
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      ubiquitousKeyValueStore = ubiquitousKeyValueStore,
      cloudKitKeyValueStore = cloudKitKeyValueStore,
      cloudBackupStore = cloudBackupStore,
      cloudBackupStoreKeys = cloudBackupStoreKeys,
      iosCloudKitBackupFeatureFlag = iosCloudKitBackupFeatureFlag,
      accountConfigService = accountConfigService
    )
  val account = iCloudAccount("ios-account-token")

  beforeTest {
    cloudStoreAccountRepository.currentAccountResult = Ok(account)
    ubiquitousKeyValueStore.reset()
    cloudKitKeyValueStore.reset()
    cloudBackupStore.reset()
    accountConfigService.reset()
    iosCloudKitBackupFeatureFlag.reset()
  }

  test("load shows both stores and includes CloudKit flag state") {
    iosCloudKitBackupFeatureFlag.setFlagValue(true)
    ubiquitousKeyValueStore.setString(account, key = "backup-b", value = "kvs-b").shouldBeOk()
    ubiquitousKeyValueStore.setString(account, key = "backup-a", value = "kvs-a").shouldBeOk()
    ubiquitousKeyValueStore.setString(account, key = "ignore-me", value = "ignored").shouldBeOk()
    cloudKitKeyValueStore.set(account, key = "backup-c", value = "ck-c".encodeUtf8()).shouldBeOk()

    val loaded = cloudBackupViewer.load().shouldBeOkOfType<CloudBackupViewerData.Loaded>()
    val ubiquitousStore = loaded.stores.first { it.storeType == UbiquitousKvs }
    val cloudKitStore = loaded.stores.first { it.storeType == CloudKit }

    loaded.iosCloudKitBackupEnabled.shouldBe(true)
    loaded.stores.map { it.storeType }.shouldContainExactly(UbiquitousKvs, CloudKit)
    ubiquitousStore.entries.map { it.key }.shouldContainExactly("backup-a", "backup-b")
    cloudKitStore.entries.map { it.key }.shouldContainExactly("backup-c")
    cloudKitStore.entries.single().value.shouldBe("ck-c")
  }

  test("deleteEntry deletes only from selected iOS store") {
    ubiquitousKeyValueStore.setString(account, key = "backup-shared", value = "kvs").shouldBeOk()
    cloudKitKeyValueStore.set(account, key = "backup-shared", value = "ck".encodeUtf8()).shouldBeOk()

    cloudBackupViewer.deleteEntry(storeType = CloudKit, key = "backup-shared").shouldBeOk()

    cloudKitKeyValueStore.get(account, key = "backup-shared").shouldBeOk().shouldBeNull()
    ubiquitousKeyValueStore.getString(account, key = "backup-shared").shouldBeOk("kvs")
  }

  test("load shows one local fake cloud store when fake cloud is active") {
    accountConfigService.setIsCloudStoreFake(true)
    cloudBackupStore.set(account, key = "backup-fake", value = "fake".encodeUtf8()).shouldBeOk()

    val loaded = cloudBackupViewer.load().shouldBeOkOfType<CloudBackupViewerData.Loaded>()
    val fakeStore = loaded.stores.single()

    loaded.stores.map { it.storeType }.shouldContainExactly(UbiquitousKvs)
    fakeStore.entries.map { it.key }.shouldContainExactly("backup-fake")
    fakeStore.entries.single().value.shouldBe("fake")
  }

  test("load returns no-cloud-account state when account is missing") {
    cloudStoreAccountRepository.currentAccountResult = Ok(null)

    cloudBackupViewer.load().shouldBeOk(CloudBackupViewerData.NoCloudAccount)
  }
})
