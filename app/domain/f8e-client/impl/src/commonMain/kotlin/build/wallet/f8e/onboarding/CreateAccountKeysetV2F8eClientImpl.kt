package build.wallet.f8e.onboarding

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.catchingResult
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
import build.wallet.f8e.serialization.toJsonString
import build.wallet.f8e.wsmIntegrityKeyVariant
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import build.wallet.logging.logError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class CreateAccountKeysetV2F8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : CreateAccountKeysetV2F8eClient {
  override suspend fun createKeyset(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    hardwareSpendingKey: HwSpendingPublicKey,
    appSpendingKey: AppSpendingPublicKey,
    network: BitcoinNetworkType,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<F8eSpendingKeyset, NetworkingError> {
    return f8eHttpClient.authenticated()
      .bodyResult<ResponseBody> {
        post("/api/v2/accounts/${fullAccountId.serverId}/keysets") {
          withDescription("Create new private keyset from f8e")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          withAppAuthKey(appAuthKey)
          withHardwareFactor(hardwareProofOfPossession)
          setRedactedBody(
            RequestBody(
              appSpendingPublicKey = appSpendingKey.key.dpub,
              hardwareSpendingPublicKey = hardwareSpendingKey.key.dpub,
              network = network.toJsonString()
            )
          )
        }
      }
      .map { response ->
        val verified = catchingResult {
          f8eHttpClient.wsmVerifier.verify(
            base58Message = DescriptorPublicKey(response.serverPublicKey).xpub,
            signature = response.serverIntegritySignature,
            keyVariant = f8eEnvironment.wsmIntegrityKeyVariant
          ).isValid
        }.getOrElse { false }

        if (!verified) {
          // Note: do not remove the '[wsm_integrity_failure]' from the message. We alert on this string in Datadog.
          logError {
            "[wsm_integrity_failure] WSM integrity signature verification failed (v2): " +
              "${response.serverIntegritySignature} : " +
              "${response.serverPublicKey} : " +
              response.keysetId
          }
          // Just log, don't fail the call.
        }

        F8eSpendingKeyset(
          keysetId = response.keysetId,
          spendingPublicKey = F8eSpendingPublicKey(dpub = response.serverPublicKey)
        )
      }
  }
}

@Serializable
private data class RequestBody(
  @SerialName("app_pub")
  private val appSpendingPublicKey: String,
  @SerialName("hardware_pub")
  private val hardwareSpendingPublicKey: String,
  @SerialName("network")
  private val network: String,
) : RedactedRequestBody

@Serializable
private data class ResponseBody(
  @SerialName("keyset_id")
  val keysetId: String,
  @SerialName("server_pub")
  val serverPublicKey: String,
  @SerialName("server_pub_integrity_sig")
  val serverIntegritySignature: String,
) : RedactedResponseBody
