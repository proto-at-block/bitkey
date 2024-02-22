package build.wallet.firmware

import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface FirmwareMetadataDao {
  /** Set active [FirmwareMetadata] */
  suspend fun setFirmwareMetadata(firmwareMetadata: FirmwareMetadata)

  /** Return active [FirmwareMetadata] [Flow]. */
  fun activeFirmwareMetadata(): Flow<Result<FirmwareMetadata?, DbError>>

  /** Return active [FirmwareMetadata]. */
  suspend fun getActiveFirmwareMetadata(): Result<FirmwareMetadata?, DbError>

  /** Remove active [FirmwareMetadata]. */
  suspend fun clear(): Result<Unit, Error>
}
