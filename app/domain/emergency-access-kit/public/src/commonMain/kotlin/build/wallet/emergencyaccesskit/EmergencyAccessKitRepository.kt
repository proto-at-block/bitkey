package build.wallet.emergencyaccesskit

import build.wallet.cloud.store.CloudStoreAccount
import com.github.michaelbull.result.Result

/**
 * A repository responsible for reading and writing the emergency access kit in the customer's
 * cloud file store.
 */
interface EmergencyAccessKitRepository {
  /**
   * Reads the current Emergency Access Kit data from the current [account]'s cloud file store.
   *
   * Returns an [EmergencyAccessKitData] the read succeeded, or an error if it does not exist or
   * failed.
   */
  suspend fun read(
    account: CloudStoreAccount,
  ): Result<EmergencyAccessKitData, EmergencyAccessKitRepositoryError>

  /**
   * Writes the given [emergencyAccessKitData] to the current [account]'s cloud file store. If
   * an emergency access kit already exists in the customer's cloud, this will update it.
   *
   * Returns success if the write operation succeeded, or an error if it failed.
   */
  suspend fun write(
    account: CloudStoreAccount,
    emergencyAccessKitData: EmergencyAccessKitData,
  ): Result<Unit, EmergencyAccessKitRepositoryError>
}
