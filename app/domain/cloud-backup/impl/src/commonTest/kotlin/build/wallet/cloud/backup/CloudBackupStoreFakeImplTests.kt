package build.wallet.cloud.backup

import bitkey.account.AccountConfigServiceFake
import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.store.KeyValueStoreFactoryFake
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

class CloudBackupStoreFakeImplTests : FunSpec({
  val keyValueStoreFactory = KeyValueStoreFactoryFake()
  val accountConfigService = AccountConfigServiceFake()
  val fakeAccount = CloudStoreAccountFake("alice")
  val otherFakeAccount = CloudStoreAccountFake("bob")
  val realAccount = object : CloudStoreAccount {}

  beforeTest {
    keyValueStoreFactory.clear()
    accountConfigService.reset()
  }

  test("fake backup store scopes values and keys by fake account") {
    val store = CloudBackupStoreFakeImpl(keyValueStoreFactory)
    val firstValue = "first".encodeUtf8()
    val secondValue = "second".encodeUtf8()

    store.set(fakeAccount, "backup", firstValue).shouldBeOk(Unit)
    store.set(otherFakeAccount, "backup", secondValue).shouldBeOk(Unit)

    store.get(fakeAccount, "backup").shouldBeOk(firstValue)
    store.get(otherFakeAccount, "backup").shouldBeOk(secondValue)
    store.keys(fakeAccount).shouldBeOk(listOf("backup"))

    store.remove(fakeAccount, "backup").shouldBeOk(Unit)
    store.get(fakeAccount, "backup").shouldBeOk(null)
    store.get(otherFakeAccount, "backup").shouldBeOk(secondValue)
  }

  test("fake backup store rejects non-fake accounts") {
    val store = CloudBackupStoreFakeImpl(keyValueStoreFactory)

    store.set(realAccount, "backup", "value".encodeUtf8()).shouldBeErrOfType<CloudError>()
    store.get(realAccount, "backup").shouldBeErrOfType<CloudError>()
    store.remove(realAccount, "backup").shouldBeErrOfType<CloudError>()
    store.keys(realAccount).shouldBeErrOfType<CloudError>()
  }

  test("delegate uses real store by default") {
    val realStore = RecordingCloudBackupStore(resultPrefix = "real")
    val fakeStore = RecordingCloudBackupStore(resultPrefix = "fake")
    val delegate = CloudBackupStoreDelegate(realStore, fakeStore, accountConfigService)

    delegate.get(realAccount, "backup").shouldBeOk("real:backup".encodeUtf8())

    realStore.calls.shouldBe(listOf("get:backup"))
    realStore.accounts.shouldBe(listOf(realAccount))
    fakeStore.calls.shouldBe(emptyList())
  }

  test("delegate uses fake store and mock account when fake cloud is enabled") {
    val realStore = RecordingCloudBackupStore(resultPrefix = "real")
    val fakeStore = RecordingCloudBackupStore(resultPrefix = "fake")
    val delegate = CloudBackupStoreDelegate(realStore, fakeStore, accountConfigService)
    accountConfigService.setIsCloudStoreFake(true)

    delegate.get(realAccount, "backup").shouldBeOk("fake:backup".encodeUtf8())

    realStore.calls.shouldBe(emptyList())
    fakeStore.calls.shouldBe(listOf("get:backup"))
    fakeStore.accounts.shouldBe(listOf(CloudStoreAccountFake.MockCloudAccount))
  }

  test("delegate uses real store when fake cloud is enabled for a non-test account") {
    val realStore = RecordingCloudBackupStore(resultPrefix = "real")
    val fakeStore = RecordingCloudBackupStore(resultPrefix = "fake")
    val delegate = CloudBackupStoreDelegate(realStore, fakeStore, accountConfigService)
    accountConfigService.setIsCloudStoreFake(true)
    accountConfigService.setActiveConfig(
      accountConfigService.defaultConfig().value.copy(isTestAccount = false)
    )

    delegate.get(realAccount, "backup").shouldBeOk("real:backup".encodeUtf8())

    realStore.calls.shouldBe(listOf("get:backup"))
    realStore.accounts.shouldBe(listOf(realAccount))
    fakeStore.calls.shouldBe(emptyList())
  }

  test("delegate uses fake store for fake accounts and preserves the fake account") {
    val realStore = RecordingCloudBackupStore(resultPrefix = "real")
    val fakeStore = RecordingCloudBackupStore(resultPrefix = "fake")
    val delegate = CloudBackupStoreDelegate(realStore, fakeStore, accountConfigService)

    delegate.get(fakeAccount, "backup").shouldBeOk("fake:backup".encodeUtf8())

    realStore.calls.shouldBe(emptyList())
    fakeStore.calls.shouldBe(listOf("get:backup"))
    fakeStore.accounts.shouldBe(listOf(fakeAccount))
  }
})

private class RecordingCloudBackupStore(
  private val resultPrefix: String,
) : CloudBackupStore {
  val calls = mutableListOf<String>()
  val accounts = mutableListOf<CloudStoreAccount>()

  override suspend fun set(
    account: CloudStoreAccount,
    key: String,
    value: ByteString,
  ): Result<Unit, CloudError> {
    calls.add("set:$key")
    accounts.add(account)
    return Ok(Unit)
  }

  override suspend fun get(
    account: CloudStoreAccount,
    key: String,
  ): Result<ByteString?, CloudError> {
    calls.add("get:$key")
    accounts.add(account)
    return Ok("$resultPrefix:$key".encodeUtf8())
  }

  override suspend fun remove(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError> {
    calls.add("remove:$key")
    accounts.add(account)
    return Ok(Unit)
  }

  override suspend fun keys(account: CloudStoreAccount): Result<List<String>, CloudError> {
    calls.add("keys")
    accounts.add(account)
    return Ok(emptyList())
  }
}
