package build.wallet.emergencyexitkit

import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudFileStore
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.emergencyexitkit.EmergencyExitKitRepositoryError.RectifiableCloudError
import build.wallet.emergencyexitkit.EmergencyExitKitRepositoryError.UnrectifiableCloudError
import build.wallet.logging.logFailure
import build.wallet.platform.data.MimeType
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError

@BitkeyInject(AppScope::class)
class EmergencyExitKitRepositoryImpl(
  private val cloudFileStore: CloudFileStore,
) : EmergencyExitKitRepository {
  override suspend fun read(
    account: CloudStoreAccount,
  ): Result<EmergencyExitKitData, EmergencyExitKitRepositoryError> =
    cloudFileStore
      .read(account, NEW_FILE_NAME)
      .result
      .map { EmergencyExitKitData(it) }
      .mapPossibleRectifiableErrors()
      .logFailure { "Error reading EEK from Cloud Storage" }

  override suspend fun write(
    account: CloudStoreAccount,
    emergencyExitKitData: EmergencyExitKitData,
  ): Result<Unit, EmergencyExitKitRepositoryError> =
    coroutineBinding {
      cloudFileStore.write(account, emergencyExitKitData.pdfData, NEW_FILE_NAME, MimeType.PDF)
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

  private fun <T> Result<T, Throwable>.mapPossibleRectifiableErrors(): Result<T, EmergencyExitKitRepositoryError> {
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
