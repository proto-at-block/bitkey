package build.wallet.firmware

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface FirmwareMetadataDao {
  /** Set active [FirmwareMetadata] */
  suspend fun setFirmwareMetadata(firmwareMetadata: FirmwareMetadata)

  /** Return active [FirmwareMetadata] [Flow]. */
  fun activeFirmwareMetadata(): Flow<Result<FirmwareMetadata?, Error>>

  /** Return active [FirmwareMetadata]. */
  suspend fun getActiveFirmwareMetadata(): Result<FirmwareMetadata?, Error>

  /** Remove active [FirmwareMetadata]. */
  suspend fun clear(): Result<Unit, Error>
}
