package build.wallet.firmware

import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.fwup.FwupDataDaoImpl
import build.wallet.fwup.McuFwupDataMock
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class FirmwareDeviceInfoDaoImplTests : FunSpec({

  val sqlDriver = inMemorySqlDriver()

  lateinit var dao: FirmwareDeviceInfoDaoImpl
  lateinit var fwupDataDao: FwupDataDaoImpl

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao = FirmwareDeviceInfoDaoImpl(databaseProvider)
    fwupDataDao = FwupDataDaoImpl(databaseProvider)
  }

  val w1DeviceInfo = FirmwareDeviceInfoMock.copy(serial = "w1-serial", version = "1.2.14")
  val w3DeviceInfo = FirmwareDeviceInfoMock.copy(serial = "w3-serial", version = "2.0.0")

  suspend fun cacheFirmwareUpdate() {
    fwupDataDao.setMcuFwupData(listOf(McuFwupDataMock), expectedSerial = w1DeviceInfo.serial)
    fwupDataDao.setMcuSequenceId(McuRole.CORE, 42u)
  }

  test("cached firmware update is discarded when the paired device changes") {
    dao.setDeviceInfo(w1DeviceInfo)
    cacheFirmwareUpdate()

    // Customer upgrades to a W3. The W1 patch was built for the W1's firmware version and
    // active slot, so it must not survive to be offered to the W3.
    dao.setDeviceInfo(w3DeviceInfo)

    fwupDataDao.getAllMcuFwupData().get().shouldNotBeNull().shouldBe(emptyList())
    // Sequence IDs track progress through the discarded firmware.
    fwupDataDao.getMcuSequenceId(McuRole.CORE).shouldBeErrOfType<Throwable>()

    dao.getDeviceInfo().get().shouldNotBeNull().serial.shouldBe("w3-serial")
  }

  test("cached firmware update survives a routine update for the same device") {
    dao.setDeviceInfo(w1DeviceInfo)
    cacheFirmwareUpdate()

    // Telemetry persists device info on nearly every NFC tap, with fields like battery
    // charge changing. That must not throw away a pending update for the same device.
    dao.setDeviceInfo(w1DeviceInfo.copy(batteryCharge = 50.0))

    fwupDataDao.getAllMcuFwupData().get().shouldNotBeNull().shouldBe(listOf(McuFwupDataMock))
    fwupDataDao.getMcuSequenceId(McuRole.CORE).get().shouldBe(42u)
  }

  test("firmware cannot be cached while no device is paired") {
    // Storing is conditional on matching the paired serial, so with no device paired there is
    // nothing to attribute an update to and the write is refused. This is what makes the
    // first-write path safe: there can be no pre-existing cache to inherit.
    val stored = fwupDataDao
      .setMcuFwupData(listOf(McuFwupDataMock), expectedSerial = w1DeviceInfo.serial)
      .get()

    stored.shouldBe(false)

    dao.setDeviceInfo(w1DeviceInfo)

    fwupDataDao.getAllMcuFwupData().get().shouldNotBeNull().shouldBe(emptyList())
  }

  test("cached firmware update is discarded when the paired device is forgotten") {
    dao.setDeviceInfo(w1DeviceInfo)
    cacheFirmwareUpdate()

    // Wiping paired hardware clears device info. The cached update is then attributable to
    // nothing, and must not be inherited by the next device paired.
    dao.clear()

    fwupDataDao.getAllMcuFwupData().get().shouldNotBeNull().shouldBe(emptyList())
    fwupDataDao.getMcuSequenceId(McuRole.CORE).shouldBeErrOfType<Throwable>()
  }

  test("firmware update fetched for a different device is not stored") {
    dao.setDeviceInfo(w3DeviceInfo)

    // Simulates a sync that read the W1's info, suspended on the network download, and only
    // now tries to store its result -- by which point the W3 is paired.
    val stored = fwupDataDao
      .setMcuFwupData(listOf(McuFwupDataMock), expectedSerial = "w1-serial")
      .get()

    stored.shouldBe(false)
    fwupDataDao.getAllMcuFwupData().get().shouldNotBeNull().shouldBe(emptyList())
  }

  test("firmware update fetched for the paired device is stored") {
    dao.setDeviceInfo(w3DeviceInfo)

    val stored = fwupDataDao
      .setMcuFwupData(listOf(McuFwupDataMock), expectedSerial = "w3-serial")
      .get()

    stored.shouldBe(true)
    fwupDataDao.getAllMcuFwupData().get().shouldNotBeNull().shouldBe(listOf(McuFwupDataMock))
  }

  test("device info round trips") {
    dao.setDeviceInfo(w1DeviceInfo)
    dao.getDeviceInfo().get().shouldNotBeNull().shouldBe(w1DeviceInfo)

    dao.clear()
    dao.getDeviceInfo().get().shouldBe(null)
  }
})
