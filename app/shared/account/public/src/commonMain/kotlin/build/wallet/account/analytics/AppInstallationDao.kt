package build.wallet.account.analytics

import build.wallet.db.DbError
import com.github.michaelbull.result.Result

/**
 * This dao currently allows only a single AppInstallation to exist as this
 * entity is meant to remain stable over the lifetime of a single app install.
 */
interface AppInstallationDao {
  /**
   * Returns the currently active App Installation. Creates an app installation if none exists
   */
  suspend fun getOrCreateAppInstallation(): Result<AppInstallation, DbError>

  /**
   * Updates the Hardware Serial Number of the active user. Creates a user if none exists
   */
  suspend fun updateAppInstallationHardwareSerialNumber(serialNumber: String): Result<Unit, DbError>
}
