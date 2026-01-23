package build.wallet.fwup

import app.cash.turbine.test
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.firmware.McuRole
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class FwupDataDaoImplTests : FunSpec({

  val sqlDriver = inMemorySqlDriver()
  val dao = FwupDataDaoImpl(BitkeyDatabaseProviderImpl(sqlDriver.factory))

  test("mcu fwup data flow") {
    val mcuFwupData1 = McuFwupDataMock
    val mcuFwupData2 = McuFwupDataMock.copy(version = "fake-2")

    dao.mcuFwupData().test {
      awaitItem().shouldBe(Ok(emptyList()))

      dao.setMcuFwupData(listOf(mcuFwupData1))
      awaitItem().component1()
        .shouldNotBeNull().shouldBe(listOf(mcuFwupData1))

      dao.setMcuFwupData(listOf(mcuFwupData2))
      awaitItem().component1()
        .shouldNotBeNull().shouldBe(listOf(mcuFwupData2))

      dao.clearAllMcuFwupData()
      awaitItem().shouldBe(Ok(emptyList()))
    }
  }

  // W3 Per-MCU Sequence ID Tests

  test("getMcuSequenceId returns error when no sequence ID stored") {
    dao.getMcuSequenceId(McuRole.CORE).shouldBeErrOfType<Throwable>()
    dao.getMcuSequenceId(McuRole.UXC).shouldBeErrOfType<Throwable>()
  }

  test("setMcuSequenceId and getMcuSequenceId for CORE") {
    dao.setMcuSequenceId(McuRole.CORE, 42u).shouldBe(Ok(Unit))
    dao.getMcuSequenceId(McuRole.CORE).get().shouldBe(42u)
  }

  test("setMcuSequenceId and getMcuSequenceId for UXC") {
    dao.setMcuSequenceId(McuRole.UXC, 99u).shouldBe(Ok(Unit))
    dao.getMcuSequenceId(McuRole.UXC).get().shouldBe(99u)
  }

  test("per-MCU sequence IDs are independent") {
    // Set different sequence IDs for each MCU
    dao.setMcuSequenceId(McuRole.CORE, 10u).shouldBe(Ok(Unit))
    dao.setMcuSequenceId(McuRole.UXC, 20u).shouldBe(Ok(Unit))

    // Verify they're stored independently
    dao.getMcuSequenceId(McuRole.CORE).get().shouldBe(10u)
    dao.getMcuSequenceId(McuRole.UXC).get().shouldBe(20u)

    // Update CORE, UXC should remain unchanged
    dao.setMcuSequenceId(McuRole.CORE, 15u).shouldBe(Ok(Unit))
    dao.getMcuSequenceId(McuRole.CORE).get().shouldBe(15u)
    dao.getMcuSequenceId(McuRole.UXC).get().shouldBe(20u)
  }

  test("clearAllMcuStates clears all per-MCU sequence IDs") {
    // Set sequence IDs for both MCUs
    dao.setMcuSequenceId(McuRole.CORE, 10u).shouldBe(Ok(Unit))
    dao.setMcuSequenceId(McuRole.UXC, 20u).shouldBe(Ok(Unit))

    // Clear all MCU states
    dao.clearAllMcuStates().shouldBe(Ok(Unit))

    // Both should now return error (no data)
    dao.getMcuSequenceId(McuRole.CORE).shouldBeErrOfType<Throwable>()
    dao.getMcuSequenceId(McuRole.UXC).shouldBeErrOfType<Throwable>()
  }
})
