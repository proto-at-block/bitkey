package build.wallet.nfc

import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class FakeHardwareStatesDaoTest : FunSpec({
  val sqlDriver = inMemorySqlDriver()
  val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
  val dao = FakeHardwareStatesDaoImpl(databaseProvider)

  beforeTest {
    dao.clear()
  }

  test("get transaction verification enabled returns null when not set") {
    val result = dao.getTransactionVerificationEnabled()
    result.isOk shouldBe true
    result.value.shouldBeNull()
  }

  test("set and get transaction verification enabled - true") {
    val setResult = dao.setTransactionVerificationEnabled(true)
    setResult.isOk shouldBe true

    val getResult = dao.getTransactionVerificationEnabled()
    getResult.isOk shouldBe true
    getResult.value.shouldNotBeNull()
    getResult.value shouldBe true
  }

  test("set and get transaction verification enabled - false") {
    val setResult = dao.setTransactionVerificationEnabled(false)
    setResult.isOk shouldBe true

    val getResult = dao.getTransactionVerificationEnabled()
    getResult.isOk shouldBe true
    getResult.value.shouldNotBeNull()
    getResult.value shouldBe false
  }

  test("update transaction verification enabled from true to false") {
    dao.setTransactionVerificationEnabled(true)
    dao.getTransactionVerificationEnabled().value shouldBe true

    dao.setTransactionVerificationEnabled(false)
    dao.getTransactionVerificationEnabled().value shouldBe false
  }

  test("update transaction verification enabled from false to true") {
    dao.setTransactionVerificationEnabled(false)
    dao.getTransactionVerificationEnabled().value shouldBe false

    dao.setTransactionVerificationEnabled(true)
    dao.getTransactionVerificationEnabled().value shouldBe true
  }

  test("clear removes transaction verification state") {
    dao.setTransactionVerificationEnabled(true)
    dao.getTransactionVerificationEnabled().value.shouldNotBeNull()

    val clearResult = dao.clear()
    clearResult.isOk shouldBe true

    dao.getTransactionVerificationEnabled().value.shouldBeNull()
  }

  test("multiple sets only maintain single row") {
    dao.setTransactionVerificationEnabled(true)
    dao.setTransactionVerificationEnabled(false)
    dao.setTransactionVerificationEnabled(true)

    val result = dao.getTransactionVerificationEnabled()
    result.isOk shouldBe true
    result.value shouldBe true
  }
})
