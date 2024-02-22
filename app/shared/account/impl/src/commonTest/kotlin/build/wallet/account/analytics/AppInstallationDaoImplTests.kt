package build.wallet.account.analytics

import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class AppInstallationDaoImplTests : DescribeSpec({
  val sqlDriver = inMemorySqlDriver()

  lateinit var dao: AppInstallationDao
  val localId = "app-installation-id"

  val appInstallation =
    AppInstallation(
      localId = localId.uppercase(),
      hardwareSerialNumber = null
    )

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao =
      AppInstallationDaoImpl(
        databaseProvider = databaseProvider,
        sequenceOf(localId, "wrong-local-id", "some-other-local-id").iterator()::next
      )
  }

  describe("getOrCreateAppInstallation") {
    it("should create app installation") {
      dao.getOrCreateAppInstallation().shouldBe(Ok(appInstallation))
    }

    it("should not create new app installation when called multiple times") {
      dao.getOrCreateAppInstallation().shouldBe(Ok(appInstallation))
      dao.getOrCreateAppInstallation().shouldBe(Ok(appInstallation))
    }
  }

  describe("updateExternalUserHardwareSerialNumber") {
    it("should create user when update hardware serial number for non-existent user") {
      val serialNumber = "hw-serial-number"

      dao.updateAppInstallationHardwareSerialNumber(serialNumber = serialNumber)
      dao.getOrCreateAppInstallation().shouldBe(
        Ok(
          AppInstallation(
            localId = localId.uppercase(),
            hardwareSerialNumber = serialNumber
          )
        )
      )
    }

    it("should update hardware serial number on existing user") {
      val serialNumber = "hw-serial-number"
      dao.getOrCreateAppInstallation().shouldBe(Ok(appInstallation))
      dao.updateAppInstallationHardwareSerialNumber(serialNumber = serialNumber)
      dao.getOrCreateAppInstallation().shouldBe(
        Ok(
          AppInstallation(
            localId = localId.uppercase(),
            hardwareSerialNumber = serialNumber
          )
        )
      )
    }
  }
})
