package build.wallet.account.analytics

import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class AppInstallationDaoMock : AppInstallationDao {
  var appInstallation: AppInstallation? = null

  override suspend fun getOrCreateAppInstallation(): Result<AppInstallation, DbError> {
    appInstallation = appInstallation ?: AppInstallation(
      localId = "local-id",
      hardwareSerialNumber = null
    )
    return Ok(appInstallation!!)
  }

  override suspend fun updateAppInstallationHardwareSerialNumber(
    serialNumber: String,
  ): Result<Unit, DbError> {
    appInstallation = appInstallation!!.copy(hardwareSerialNumber = serialNumber)
    return Ok(Unit)
  }

  fun reset() {
    appInstallation = null
  }
}