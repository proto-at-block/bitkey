package build.wallet.cloud

import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudKeyValueStoreFake
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec

class CloudKeyValueStoreFakeTests : FunSpec({
  val store = CloudKeyValueStoreFake()
  val account1 = CloudAccountMock(instanceId = "alice")
  val account2 = CloudAccountMock(instanceId = "bob")

  beforeTest {
    store.reset()
  }

  test("set value") {
    store.setString(account1, key = "age", value = "9999").shouldBeOk(Unit)

    store.getString(account1, key = "age").shouldBeOk("9999")
  }

  test("override value") {
    store.setString(account1, key = "age", value = "9999").shouldBeOk(Unit)
    store.setString(account1, key = "age", value = "1111").shouldBeOk(Unit)

    store.getString(account1, key = "age").shouldBeOk("1111")
  }

  test("remove existing value") {
    store.setString(account1, key = "age", value = "9999").shouldBeOk(Unit)

    store.removeString(account1, key = "age").shouldBeOk(Unit)

    store.getString(account1, key = "age").shouldBeOk(null)
  }

  test("remove existing value from different cloud account") {
    store.setString(account1, key = "age", value = "9999").shouldBeOk(Unit)
    store.setString(account2, key = "age", value = "1111").shouldBeOk(Unit)

    store.removeString(account1, key = "age").shouldBeOk(Unit)

    store.getString(account1, key = "age").shouldBeOk(null)
    store.getString(account2, key = "age").shouldBeOk("1111")
  }

  test("no effect when trying to remove non-existent value") {
    store.removeString(account1, key = "age").shouldBeOk(Unit)
  }
})
