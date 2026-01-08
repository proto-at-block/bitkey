package build.wallet.f8e.recovery

import bitkey.backup.DescriptorBackup
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class ListKeysetsF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : ListKeysetsF8eClient {
  override suspend fun listKeysets(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<ListKeysetsResponse, NetworkingError> {
    return f8eHttpClient.authenticated()
      .bodyResult<ResponseBody> {
        get("/api/accounts/${fullAccountId.serverId}/keysets") {
          withDescription("Get keysets from f8e")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
        }
      }
      .map { body ->
        ListKeysetsResponse(
          keysets = body.keysets,
          wrappedSsek = body.wrappedSsek,
          descriptorBackups = body.descriptorBackups,
          activeKeysetId = body.activeKeysetId
        )
      }
  }

  @Serializable
  private data class ResponseBody(
    @SerialName("keysets")
    val keysets: List<RemoteKeyset>,
    @SerialName("wrapped_ssek")
    val wrappedSsek: SealedSsek?,
    @SerialName("descriptor_backups")
    val descriptorBackups: List<DescriptorBackup>,
    @SerialName("active_keyset_id")
    val activeKeysetId: String,
  ) : RedactedResponseBody
}
