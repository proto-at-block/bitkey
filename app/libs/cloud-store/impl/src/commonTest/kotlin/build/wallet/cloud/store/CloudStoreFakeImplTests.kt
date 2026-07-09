package build.wallet.cloud.store

import bitkey.account.AccountConfigServiceFake
import build.wallet.platform.data.FileManagerMock
import build.wallet.platform.data.MimeType
import build.wallet.store.KeyValueStoreFactoryFake
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class CloudStoreFakeImplTests : FunSpec({
  val keyValueStoreFactory = KeyValueStoreFactoryFake()
  val fileManager = FileManagerMock()
  val accountConfigService = AccountConfigServiceFake()
  val fakeAccount = CloudStoreAccountFake("alice")
  val otherFakeAccount = CloudStoreAccountFake("bob")
  val realAccount = object : CloudStoreAccount {}
  val serviceProvider = object : CloudStoreServiceProvider {
    override val name = "test"
  }

  beforeTest {
    keyValueStoreFactory.clear()
    fileManager.files.clear()
    accountConfigService.reset()
  }

  test("fake account repository returns stable mock account by default") {
    val repository = CloudStoreAccountRepositoryFakeImpl(keyValueStoreFactory)

    repository.currentAccount(serviceProvider)
      .shouldBeOk(CloudStoreAccountFake.MockCloudAccount)
  }

  test("fake account repository supports set and clear") {
    val repository = CloudStoreAccountRepositoryFakeImpl(keyValueStoreFactory)

    repository.set(fakeAccount).shouldBeOk(Unit)
    CloudStoreAccountRepositoryFakeImpl(keyValueStoreFactory)
      .currentAccount(serviceProvider)
      .shouldBeOk(fakeAccount)

    repository.clear().shouldBeOk(Unit)
    repository.currentAccount(serviceProvider)
      .shouldBeOk(CloudStoreAccountFake.MockCloudAccount)
  }

  test("fake file store supports write, exists, read, and remove per fake account") {
    val store = CloudFileStoreFakeImpl(fileManager)
    val bytes = "hello".encodeUtf8()

    store.exists(fakeAccount, "backup.json").shouldBeFileOk(false)
    store.exists(otherFakeAccount, "backup.json").shouldBeFileOk(false)
    store.write(fakeAccount, bytes, "backup.json", MimeType.JSON).shouldBeFileOk(Unit)
    store.exists(fakeAccount, "backup.json").shouldBeFileOk(true)
    store.exists(otherFakeAccount, "backup.json").shouldBeFileOk(false)
    store.read(fakeAccount, "backup.json").shouldBeFileOk(bytes)
    store.remove(fakeAccount, "backup.json").shouldBeFileOk(Unit)
    store.exists(fakeAccount, "backup.json").shouldBeFileOk(false)
  }

  test("fake file store rejects non-fake accounts") {
    val store = CloudFileStoreFakeImpl(fileManager)

    store.exists(realAccount, "backup.json").shouldBeFileErr()
    store.read(realAccount, "backup.json").shouldBeFileErr()
    store.remove(realAccount, "backup.json").shouldBeFileErr()
    store.write(realAccount, "hello".encodeUtf8(), "backup.json", MimeType.JSON)
      .shouldBeFileErr()
  }

  test("file delegate uses real store by default") {
    val realStore = RecordingCloudFileStore(result = false)
    val fakeStore = RecordingCloudFileStore(result = true)
    val delegate = CloudFileStoreDelegate(realStore, fakeStore, accountConfigService)

    delegate.exists(realAccount, "backup.json").shouldBeFileOk(false)

    realStore.calls.shouldBe(listOf("exists:backup.json"))
    realStore.accounts.shouldBe(listOf(realAccount))
    fakeStore.calls.shouldBe(emptyList())
  }

  test("file delegate uses fake store for fake accounts and preserves the fake account") {
    val realStore = RecordingCloudFileStore(result = false)
    val fakeStore = RecordingCloudFileStore(result = true)
    val delegate = CloudFileStoreDelegate(realStore, fakeStore, accountConfigService)

    delegate.exists(fakeAccount, "backup.json").shouldBeFileOk(true)

    realStore.calls.shouldBe(emptyList())
    fakeStore.calls.shouldBe(listOf("exists:backup.json"))
    fakeStore.accounts.shouldBe(listOf(fakeAccount))
  }

  test("file delegate uses fake store and mock account when fake cloud is enabled") {
    val realStore = RecordingCloudFileStore(result = false)
    val fakeStore = RecordingCloudFileStore(result = true)
    val delegate = CloudFileStoreDelegate(realStore, fakeStore, accountConfigService)
    accountConfigService.setIsCloudStoreFake(true)

    delegate.exists(realAccount, "backup.json").shouldBeFileOk(true)

    realStore.calls.shouldBe(emptyList())
    fakeStore.calls.shouldBe(listOf("exists:backup.json"))
    fakeStore.accounts.shouldBe(listOf(CloudStoreAccountFake.MockCloudAccount))
  }

  test("file delegate uses real store when fake cloud is enabled for a non-test account") {
    val realStore = RecordingCloudFileStore(result = false)
    val fakeStore = RecordingCloudFileStore(result = true)
    val delegate = CloudFileStoreDelegate(realStore, fakeStore, accountConfigService)
    accountConfigService.enableFakeCloudForNonTestAccount()

    delegate.exists(realAccount, "backup.json").shouldBeFileOk(false)

    realStore.calls.shouldBe(listOf("exists:backup.json"))
    realStore.accounts.shouldBe(listOf(realAccount))
    fakeStore.calls.shouldBe(emptyList())
  }

  test("account repository delegate uses fake repository when enabled") {
    val realRepository = RecordingCloudStoreAccountRepository(realAccount)
    val fakeRepository = RecordingCloudStoreAccountRepository(fakeAccount)
    val delegate = CloudStoreAccountRepositoryDelegate(
      realRepository = realRepository,
      fakeRepository = fakeRepository,
      accountConfigService = accountConfigService
    )
    accountConfigService.setIsCloudStoreFake(true)

    delegate.currentAccount(serviceProvider).shouldBeOk(fakeAccount)

    realRepository.calls.shouldBe(emptyList())
    fakeRepository.calls.shouldBe(listOf("currentAccount"))
  }

  test("account repository delegate uses real repository when fake cloud is enabled for a non-test account") {
    val realRepository = RecordingCloudStoreAccountRepository(realAccount)
    val fakeRepository = RecordingCloudStoreAccountRepository(fakeAccount)
    val delegate = CloudStoreAccountRepositoryDelegate(
      realRepository = realRepository,
      fakeRepository = fakeRepository,
      accountConfigService = accountConfigService
    )
    accountConfigService.enableFakeCloudForNonTestAccount()

    delegate.currentAccount(serviceProvider).shouldBeOk(realAccount)

    realRepository.calls.shouldBe(listOf("currentAccount"))
    fakeRepository.calls.shouldBe(emptyList())
  }
})

private suspend fun AccountConfigServiceFake.enableFakeCloudForNonTestAccount() {
  setIsCloudStoreFake(true)
  setActiveConfig(defaultConfig().value.copy(isTestAccount = false))
}

private fun <T : Any> CloudFileStoreResult<T>.shouldBeFileOk(expected: T) {
  this.shouldBe(CloudFileStoreResult.Ok(expected))
}

private fun <T : Any> CloudFileStoreResult<T>.shouldBeFileErr() {
  (this is CloudFileStoreResult.Err).shouldBe(true)
}

private class RecordingCloudFileStore(
  private val result: Boolean,
) : CloudFileStore {
  val calls = mutableListOf<String>()
  val accounts = mutableListOf<CloudStoreAccount>()

  override suspend fun exists(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<Boolean> {
    calls.add("exists:$fileName")
    accounts.add(account)
    return CloudFileStoreResult.Ok(result)
  }

  override suspend fun read(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<okio.ByteString> {
    calls.add("read:$fileName")
    accounts.add(account)
    return CloudFileStoreResult.Ok("bytes".encodeUtf8())
  }

  override suspend fun remove(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<Unit> {
    calls.add("remove:$fileName")
    accounts.add(account)
    return CloudFileStoreResult.Ok(Unit)
  }

  override suspend fun write(
    account: CloudStoreAccount,
    bytes: okio.ByteString,
    fileName: String,
    mimeType: MimeType,
  ): CloudFileStoreResult<Unit> {
    calls.add("write:$fileName")
    accounts.add(account)
    return CloudFileStoreResult.Ok(Unit)
  }
}

private class RecordingCloudStoreAccountRepository(
  private val account: CloudStoreAccount,
) : CloudStoreAccountRepository {
  val calls = mutableListOf<String>()

  override suspend fun currentAccount(
    cloudStoreServiceProvider: CloudStoreServiceProvider,
  ): Result<CloudStoreAccount?, CloudStoreAccountError> {
    calls.add("currentAccount")
    return Ok(account)
  }

  override suspend fun clear(): Result<Unit, Throwable> {
    calls.add("clear")
    return Ok(Unit)
  }
}
