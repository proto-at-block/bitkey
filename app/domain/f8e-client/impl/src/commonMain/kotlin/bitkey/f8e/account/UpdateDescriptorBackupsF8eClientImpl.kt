package bitkey.f8e.account

import bitkey.backup.DescriptorBackup
import build.wallet.account.UpdateDescriptorBackupError
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withAppAuthKey
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
    sealedSsek: SealedSsek,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    hwKeyProof: HwFactorProofOfPossession,
  ): Result<Unit, UpdateDescriptorBackupError> =
    f8eHttpClient.authenticated()
      .bodyResult<EmptyResponseBody> {
        put("/api/accounts/${accountId.serverId}/descriptor-backups") {
          withDescription("Update descriptor backups on f8e")
          withEnvironment(f8eEnvironment)
          withAccountId(accountId)
          setRedactedBody(RequestBody(sealedSsek, descriptorBackups))
          withAppAuthKey(appAuthKey)
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
    // The right terminology to refer to a key that is wrapped by another key is to say that is is
    // "wrapped", but in this case, we use "sealed" to keep the naming consistent with what already
    // exists for CSEK.
    @SerialName("wrapped_ssek")
    val sealedSsek: SealedSsek,
    @SerialName("descriptor_backups")
    val descriptorBackups: List<DescriptorBackup>,
  ) : RedactedRequestBody
}
