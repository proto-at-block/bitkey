package build.wallet.account.analytics

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * This dao currently allows only a single AppInstallation to exist as this
 * entity is meant to remain stable over the lifetime of a single app install.
 */
interface AppInstallationDao {
  /**
   * Returns a reactive flow of the current [AppInstallation], emitting whenever the underlying
   * data changes (e.g. hardware serial number is updated after pairing or cloud backup restoration).
   */
  fun appInstallation(): Flow<Result<AppInstallation?, Error>>

  /**
   * Returns the currently active App Installation. Creates an app installation if none exists
   */
  suspend fun getOrCreateAppInstallation(): Result<AppInstallation, Error>

  /**
   * Updates the Hardware Serial Number of the active user. Creates a user if none exists
   */
  suspend fun updateAppInstallationHardwareSerialNumber(serialNumber: String): Result<Unit, Error>
}
