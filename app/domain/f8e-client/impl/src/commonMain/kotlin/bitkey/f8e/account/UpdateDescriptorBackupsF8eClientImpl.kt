package bitkey.f8e.account

import bitkey.backup.DescriptorBackup
import build.wallet.account.UpdateDescriptorBackupError
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.client.plugins.withHardwareFactor
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.*
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import io.ktor.client.request.*
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class UpdateDescriptorBackupsF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : UpdateDescriptorBackupsF8eClient {
  override suspend fun update(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
    descriptorBackups: List<DescriptorBackup>,
    hwKeyProof: HwFactorProofOfPossession,
  ): Result<Unit, UpdateDescriptorBackupError> =
    f8eHttpClient.authenticated()
      .bodyResult<EmptyResponseBody> {
        put("/api/accounts/${accountId.serverId}/descriptor-backups") {
          withDescription("Update descriptor backups on f8e")
          withEnvironment(f8eEnvironment)
          withAccountId(accountId)
          setRedactedBody(RequestBody(descriptorBackups))
          withHardwareFactor(hwKeyProof)
        }
      }
      .mapUnit()
      .mapError { error ->
        if (error is HttpError.ClientError) {
          when (error.response.status) {
            HttpStatusCode.NotFound -> UpdateDescriptorBackupError.KeysetIdNotFound(error)
            HttpStatusCode.BadRequest -> UpdateDescriptorBackupError.MissingKeysetId(error)
            HttpStatusCode.Forbidden -> UpdateDescriptorBackupError.HardwareFactorRequired(error)
            else -> UpdateDescriptorBackupError.Unspecified(error)
          }
        } else {
          UpdateDescriptorBackupError.Unspecified(error)
        }
      }

  @Serializable
  private data class RequestBody(
    @SerialName("descriptor_backups")
    val descriptorBackups: List<DescriptorBackup>,
  ) : RedactedRequestBody
}
