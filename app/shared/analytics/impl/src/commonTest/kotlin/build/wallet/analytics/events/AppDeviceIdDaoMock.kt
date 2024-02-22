package build.wallet.analytics.events

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * A dao for storing and retrieving the AppDeviceID.
 */
class AppDeviceIdDaoMock : AppDeviceIdDao {
  var appDeviceId = "app_device_id_1"

  override suspend fun getOrCreateAppDeviceIdIfNotExists(): Result<String, Throwable> {
    return Ok(appDeviceId)
  }
}
