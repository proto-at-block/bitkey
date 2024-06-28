package build.wallet.f8e.onboarding.frost

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.f8e.SoftwareAccountId
import build.wallet.bitkey.f8e.SoftwareKeysetId
import build.wallet.catchingResult
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.logging.withDescription
import build.wallet.f8e.serialization.toJsonString
import build.wallet.f8e.wsmIntegrityKeyVariant
import build.wallet.ktor.result.*
import build.wallet.logging.log
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class ContinueDistributedKeygenF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : ContinueDistributedKeygenF8eClient {
  override suspend fun continueDistributedKeygen(
    f8eEnvironment: F8eEnvironment,
    networkType: BitcoinNetworkType,
    accountId: SoftwareAccountId,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    keysetId: SoftwareKeysetId,
  ): Result<F8eSpendingKeyset, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        f8eEnvironment = f8eEnvironment,
        accountId = accountId,
        appFactorProofOfPossessionAuthKey = appAuthKey
      )
      .bodyResult<ResponseBody> {
        post(urlString = "/api/accounts/${accountId.serverId}/distributed-keygen/${keysetId.keysetId}") {
          withDescription("Continue distributed keygen")
          setRedactedBody(
            RequestBody(
              network = networkType.toJsonString(),
              appDpub = appAuthKey.value
            )
          )
        }
      }
      .map { response ->
        val verified = catchingResult {
          f8eHttpClient.wsmVerifier.verify(
            base58Message = DescriptorPublicKey(response.spendingDpub).xpub,
            signature = response.spendingSig,
            keyVariant = f8eEnvironment.wsmIntegrityKeyVariant
          ).isValid
        }.getOrElse {
          false
        }

        if (!verified) {
          // Note: do not remove the '[wsm_integrity_failure]' from the message. We alert on this string in Datadog.
          log {
            "[wsm_integrity_failure] WSM integrity signature verification failed: " +
              "${response.spendingSig} : " +
              "${response.spendingDpub} : " +
              keysetId
          }
          // Just log, don't fail the call.
        }

        F8eSpendingKeyset(
          keysetId = keysetId.keysetId,
          spendingPublicKey = F8eSpendingPublicKey(response.spendingDpub)
        )
      }
  }

  @Serializable
  private data class RequestBody(
    @SerialName("network")
    val network: String,
    @SerialName("app")
    val appDpub: String,
  ) : RedactedRequestBody

  @Serializable
  private data class ResponseBody(
    @SerialName("spending")
    val spendingDpub: String,
    @SerialName("spending_sig")
    val spendingSig: String,
  ) : RedactedResponseBody
}
