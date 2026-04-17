package build.wallet.debug.cloud

import build.wallet.cloud.backup.CloudBackupStoreFake
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudStoreAccountError
import build.wallet.cloud.store.CloudStoreAccountRepositoryMock
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import build.wallet.testing.shouldBeOkOfType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import okio.ByteString.Companion.encodeUtf8

class CloudBackupViewerImplTests : FunSpec({
  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
  val cloudBackupStore = CloudBackupStoreFake()
  val cloudBackupStoreKeys = CloudBackupStoreKeysFake()
  val cloudBackupViewer =
    CloudBackupViewerImpl(
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      cloudBackupStore = cloudBackupStore,
      cloudBackupStoreKeys = cloudBackupStoreKeys
    )
  val account = CloudAccountMock(instanceId = "account-1")

  beforeTest {
    cloudStoreAccountRepository.currentAccountResult = Ok(account)
    cloudBackupStore.reset()
  }

  test("load returns sorted backup keys only") {
    cloudBackupStore.set(account, key = "backup-z", value = "z".encodeUtf8()).shouldBeOk()
    cloudBackupStore.set(account, key = "backup-a", value = "a".encodeUtf8()).shouldBeOk()
    cloudBackupStore.set(account, key = "archive-1", value = "archived".encodeUtf8()).shouldBeOk()
    cloudBackupStore.set(account, key = "ignore-me", value = "ignored".encodeUtf8()).shouldBeOk()

    val loaded = cloudBackupViewer.load().shouldBeOkOfType<CloudBackupViewerData.Loaded>()
    val store = loaded.stores.single()

    loaded.iosCloudKitBackupEnabled.shouldBe(null)
    store.storeType.shouldBe(CloudStore)
    store.entries.map { it.key }.shouldContainExactly("archive-1", "backup-a", "backup-z")
    store.entries.map { it.value }.shouldContainExactly("archived", "a", "z")
  }

  test("deleteEntry removes only selected key") {
    cloudBackupStore.set(account, key = "backup-key", value = "value".encodeUtf8()).shouldBeOk()

    cloudBackupViewer.deleteEntry(storeType = CloudStore, key = "backup-key").shouldBeOk()

    cloudBackupStore.get(account, key = "backup-key").shouldBeOk().shouldBeNull()
  }

  test("load returns no-cloud-account state when account is missing") {
    cloudStoreAccountRepository.currentAccountResult = Ok(null)

    cloudBackupViewer.load().shouldBeOk(CloudBackupViewerData.NoCloudAccount)
  }

  test("load returns load error when account lookup fails") {
    cloudStoreAccountRepository.currentAccountResult = Err(CloudStoreAccountError())

    val error = cloudBackupViewer.load().shouldBeErrOfType<CloudBackupViewerLoadError>()
    error.message.shouldContain("Failed to load cloud account")
  }
})
