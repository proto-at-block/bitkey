package build.wallet.analytics.events

import com.github.michaelbull.result.Result

/**
 * A dao for storing and retrieving the AppDeviceID.
 * TODO(W-3943) Merge AppInstallationDao and AppDeviceIdDao into one dao
 */
interface AppDeviceIdDao {
  suspend fun getOrCreateAppDeviceIdIfNotExists(): Result<String, Throwable>
}
