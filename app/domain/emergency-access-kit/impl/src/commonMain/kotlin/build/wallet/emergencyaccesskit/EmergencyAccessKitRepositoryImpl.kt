package build.wallet.emergencyaccesskit

import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudFileStore
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.emergencyaccesskit.EmergencyAccessKitRepositoryError.RectifiableCloudError
import build.wallet.emergencyaccesskit.EmergencyAccessKitRepositoryError.UnrectifiableCloudError
import build.wallet.logging.logFailure
import build.wallet.platform.data.MimeType
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError

@BitkeyInject(AppScope::class)
class EmergencyAccessKitRepositoryImpl(
  private val cloudFileStore: CloudFileStore,
) : EmergencyAccessKitRepository {
  override suspend fun read(
    account: CloudStoreAccount,
  ): Result<EmergencyAccessKitData, EmergencyAccessKitRepositoryError> =
    cloudFileStore
      .read(account, NEW_FILE_NAME)
      .result
      .map { EmergencyAccessKitData(it) }
      .mapPossibleRectifiableErrors()
      .logFailure { "Error reading EEK from Cloud Storage" }

  override suspend fun write(
    account: CloudStoreAccount,
    emergencyAccessKitData: EmergencyAccessKitData,
  ): Result<Unit, EmergencyAccessKitRepositoryError> =
    coroutineBinding {
      cloudFileStore.write(account, emergencyAccessKitData.pdfData, NEW_FILE_NAME, MimeType.PDF)
        .result
        .mapPossibleRectifiableErrors()
        .bind()

      // Check if the old file exists
      val oldFileExists = cloudFileStore.exists(account, ORIGINAL_FILE_NAME)
        .result
        .mapPossibleRectifiableErrors()
        .bind()

      if (oldFileExists) {
        // If old file exists, remove it
        cloudFileStore.remove(account, ORIGINAL_FILE_NAME)
          .result
          .mapPossibleRectifiableErrors()
          .bind()
      }
    }
      .logFailure { "Error writing EEK to Cloud Storage" }

  private companion object {
    const val ORIGINAL_FILE_NAME = "Emergency Access Kit.pdf"
    const val NEW_FILE_NAME = "Emergency Exit Kit.pdf"
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
