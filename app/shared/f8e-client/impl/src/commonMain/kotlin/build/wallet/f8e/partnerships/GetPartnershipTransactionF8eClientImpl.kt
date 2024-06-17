package build.wallet.f8e.partnerships

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnershipTransactionId
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

class GetPartnershipTransactionF8eClientImpl(
  private val client: F8eHttpClient,
) : GetPartnershipTransactionF8eClient {
  override suspend fun getPartnershipTransaction(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    partner: PartnerId,
    partnershipTransactionId: PartnershipTransactionId,
  ): Result<F8ePartnershipTransaction, NetworkingError> {
    return client
      .authenticated(
        accountId = fullAccountId,
        f8eEnvironment = f8eEnvironment
      )
      .bodyResult<PartnershipTransactionResponse> {
        get("/api/partnerships/partners/${partner.value}/transactions/${partnershipTransactionId.value}") {
          withDescription("Get partnership transaction")
        }
      }
      .map {
        it.transaction
      }
  }

  @Serializable
  internal data class PartnershipTransactionResponse(
    val transaction: F8ePartnershipTransaction,
  ) : RedactedResponseBody
}
