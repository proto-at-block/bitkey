package build.wallet.emergencyexitkit

import build.wallet.cloud.store.CloudStoreAccount
import com.github.michaelbull.result.Result

/**
 * A repository responsible for reading and writing the Emergency Exit Kit in the customer's
 * cloud file store.
 */
interface EmergencyExitKitRepository {
  /**
   * Reads the current Emergency Exit Kit data from the current [account]'s cloud file store.
   *
   * Returns an [EmergencyExitKitData] the read succeeded, or an error if it does not exist or
   * failed.
   */
  suspend fun read(
    account: CloudStoreAccount,
  ): Result<EmergencyExitKitData, EmergencyExitKitRepositoryError>

  /**
   * Writes the given [emergencyExitKitData] to the current [account]'s cloud file store. If
   * an Emergency Exit Kit already exists in the customer's cloud, this will update it.
   *
   * Returns success if the write operation succeeded, or an error if it failed.
   */
  suspend fun write(
    account: CloudStoreAccount,
    emergencyExitKitData: EmergencyExitKitData,
  ): Result<Unit, EmergencyExitKitRepositoryError>
}
