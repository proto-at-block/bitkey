package bitkey.firmware

import app.cash.turbine.test
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.firmware.UnlockInfo
import build.wallet.firmware.UnlockMethod
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope

class HardwareUnlockInfoServiceImplTests : FunSpec({
  val clock = ClockFake()
  val dao = HardwareUnlockInfoDao(
    databaseProvider = BitkeyDatabaseProviderImpl(inMemorySqlDriver().factory)
  )

  val unlockInfoList = listOf(
    UnlockInfo(UnlockMethod.BIOMETRICS, 0),
    UnlockInfo(UnlockMethod.BIOMETRICS, 1),
    UnlockInfo(UnlockMethod.UNLOCK_SECRET, null)
  )

  val hardwareUnlockInfoService = HardwareUnlockInfoServiceImpl(
    dao = dao,
    clock = clock,
    appCoroutineScope = TestScope()
  )

  test("replaceAllUnlockInfo should replace all unlock info") {
    val hardwareUnlockInfoService = hardwareUnlockInfoService

    hardwareUnlockInfoService.replaceAllUnlockInfo(unlockInfoList)

    dao.getAllUnlockInfo().value shouldBe unlockInfoList
  }

  test("clear should clear all unlock info") {
    val hardwareUnlockInfoService = hardwareUnlockInfoService

    hardwareUnlockInfoService.replaceAllUnlockInfo(unlockInfoList)
    hardwareUnlockInfoService.clear()

    dao.getAllUnlockInfo().value.shouldBeEmpty()
  }

  test("countUnlockInfo return correct counts") {
    val hardwareUnlockInfoService = hardwareUnlockInfoService

    hardwareUnlockInfoService.replaceAllUnlockInfo(unlockInfoList)

    hardwareUnlockInfoService.countUnlockInfo(UnlockMethod.BIOMETRICS).test { awaitItem() shouldBe 2 }
    hardwareUnlockInfoService.countUnlockInfo(UnlockMethod.UNLOCK_SECRET).test {
      awaitItem() shouldBe 1
    }
  }
})
