package build.wallet.account.analytics

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class AppInstallationDaoMock : AppInstallationDao {
  private val appInstallationFlow = MutableStateFlow<Result<AppInstallation?, Error>>(Ok(null))

  var appInstallation: AppInstallation?
    get() = appInstallationFlow.value.get()
    set(value) {
      appInstallationFlow.value = Ok(value)
    }

  /** Tracks all calls to [updateAppInstallationHardwareSerialNumber] with their serial numbers. */
  val updateSerialNumberCalls = mutableListOf<String>()

  override fun appInstallation(): Flow<Result<AppInstallation?, Error>> {
    return appInstallationFlow
  }

  override suspend fun getOrCreateAppInstallation(): Result<AppInstallation, Error> {
    val current = appInstallation ?: AppInstallation(
      localId = "local-id",
      hardwareSerialNumber = null
    )
    appInstallation = current
    return Ok(current)
  }

  override suspend fun updateAppInstallationHardwareSerialNumber(
    serialNumber: String,
  ): Result<Unit, Error> {
    updateSerialNumberCalls.add(serialNumber)
    val current = appInstallation ?: AppInstallation(
      localId = "local-id",
      hardwareSerialNumber = null
    )
    appInstallation = current.copy(hardwareSerialNumber = serialNumber)
    return Ok(Unit)
  }

  fun reset() {
    appInstallation = null
    updateSerialNumberCalls.clear()
  }
}
