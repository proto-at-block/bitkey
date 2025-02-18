package build.wallet.inheritance

import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class InheritanceUpsellViewDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()

  lateinit var dao: InheritanceUpsellViewDao

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao = InheritanceUpsellViewDaoImpl(databaseProvider)
  }

  test("get returns false when no entry exists") {
    dao.get("test-id").value.shouldBe(false)
  }

  test("insert and get") {
    dao.insert("test-id").shouldBe(Ok(Unit))
    dao.get("test-id").value.shouldBe(false)
  }

  test("setViewed") {
    dao.insert("test-id").shouldBe(Ok(Unit))
    dao.get("test-id").value.shouldBe(false)

    dao.setViewed("test-id").shouldBe(Ok(Unit))
    dao.get("test-id").value.shouldBe(true)
  }
})
