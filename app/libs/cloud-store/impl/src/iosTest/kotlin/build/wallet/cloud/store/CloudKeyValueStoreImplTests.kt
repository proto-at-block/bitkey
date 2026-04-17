package build.wallet.cloud.store

import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec

class CloudKeyValueStoreImplTests : FunSpec({
  val ubiquitousStoreFake = UbiquitousKeyValueStoreFake()
  val account = iCloudAccount(ubiquityIdentityToken = "test-token")

  val store = CloudKeyValueStoreImpl(
    ubiquitousKeyValueStore = ubiquitousStoreFake
  )

  beforeTest {
    ubiquitousStoreFake.reset()
  }

  test("setString writes to KVS") {
    store.setString(account, key = "test-key", value = "test-value").shouldBeOk()

    ubiquitousStoreFake.getString(account, key = "test-key").shouldBeOk("test-value")
  }

  test("getString reads from KVS") {
    ubiquitousStoreFake.setString(account, key = "test-key", value = "kvs-value")

    store.getString(account, key = "test-key").shouldBeOk("kvs-value")
  }

  test("removeString removes from KVS") {
    ubiquitousStoreFake.setString(account, key = "test-key", value = "test-value")

    store.removeString(account, key = "test-key").shouldBeOk()

    ubiquitousStoreFake.getString(account, key = "test-key").shouldBeOk(null)
  }

  test("keys reads from KVS") {
    ubiquitousStoreFake.setString(account, key = "kvs-key", value = "value")

    store.keys(account).shouldBeOk(listOf("kvs-key"))
  }

  test("returns KVS errors") {
    ubiquitousStoreFake.returnError = true

    store.setString(account, key = "test-key", value = "test-value").shouldBeErrOfType<CloudError>()
    store.getString(account, key = "test-key").shouldBeErrOfType<CloudError>()
    store.removeString(account, key = "test-key").shouldBeErrOfType<CloudError>()
    store.keys(account).shouldBeErrOfType<CloudError>()
  }
})
