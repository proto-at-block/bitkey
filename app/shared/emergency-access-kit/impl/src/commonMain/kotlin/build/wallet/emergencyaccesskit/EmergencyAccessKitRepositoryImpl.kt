package build.wallet.emergencyaccesskit

import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudFileStore
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.emergencyaccesskit.EmergencyAccessKitRepositoryError.RectifiableCloudError
import build.wallet.emergencyaccesskit.EmergencyAccessKitRepositoryError.UnrectifiableCloudError
import build.wallet.logging.logFailure
import build.wallet.platform.data.MimeType
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError

class EmergencyAccessKitRepositoryImpl(
  private val cloudFileStore: CloudFileStore,
) : EmergencyAccessKitRepository {
  override suspend fun read(
    account: CloudStoreAccount,
  ): Result<EmergencyAccessKitData, EmergencyAccessKitRepositoryError> =
    cloudFileStore
      .read(account, FILE_NAME)
      .result
      .map { EmergencyAccessKitData(it) }
      .mapPossibleRectifiableErrors()
      .logFailure { "Error reading EAK from Cloud Storage" }

  override suspend fun write(
    account: CloudStoreAccount,
    emergencyAccessKitData: EmergencyAccessKitData,
  ): Result<Unit, EmergencyAccessKitRepositoryError> =
    cloudFileStore
      .write(account, emergencyAccessKitData.pdfData, FILE_NAME, MimeType.PDF)
      .result
      .mapPossibleRectifiableErrors()
      .logFailure { "Error writing EAK to Cloud Storage" }

  private companion object {
    const val FILE_NAME = "Emergency Access Kit.pdf"
  }

  private fun <T> Result<T, Throwable>.mapPossibleRectifiableErrors(): Result<T, EmergencyAccessKitRepositoryError> {
    return mapError { error ->
      when (error) {
        is CloudError -> {
          error.rectificationData
            ?.let { rectificationData ->
              RectifiableCloudError(error, rectificationData)
            }
            ?: UnrectifiableCloudError(error)
        }

        else -> UnrectifiableCloudError(error)
      }
    }
  }
}
