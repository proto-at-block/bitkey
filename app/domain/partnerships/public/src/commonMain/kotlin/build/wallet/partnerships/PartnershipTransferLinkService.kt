package build.wallet.partnerships

import com.github.michaelbull.result.Result

interface PartnershipTransferLinkService {
  /**
   * Looks up the corresponding [PartnerInfo] for a given partner name.
   */
  suspend fun getPartnerInfoForPartner(partner: String): Result<PartnerInfo, GetPartnerInfoError>

  /**
   * Processes a partner transfer link request and returns a redirect back to that partner.
   */
  suspend fun processTransferLink(
    partnerInfo: PartnerInfo,
    tokenizedSecret: String,
  ): Result<TransferLinkRedirectInfo, ProcessTransferLinkError>
}

sealed class ProcessTransferLinkError : Error() {
  /**
   * A retryable error
   */
  data class Retryable(override val cause: Error) : ProcessTransferLinkError()

  /**
   * An error that cannot be retried; the user needs to restart the transfer link
   * process from the partner app
   */
  data class NotRetryable(override val cause: Error) : ProcessTransferLinkError()
}

sealed class GetPartnerInfoError : Error() {
  /**
   * There is no valid full account available
   */
  data class NoFullAccountFound(override val cause: Throwable) : GetPartnerInfoError()

  /**
   * No matching partner
   */
  data class PartnerNotFound(override val cause: Throwable) : GetPartnerInfoError()

  /**
   * Retryable network error
   */
  data class NetworkingError(override val cause: build.wallet.ktor.result.NetworkingError) :
    GetPartnerInfoError()
}

/**
 * Response from creating a transfer link, including redirect information
 *
 * @property redirectMethod - How the user should be redirected to the partner
 * @property partnerName - Name of the partner for display purposes
 */
data class TransferLinkRedirectInfo(
  val redirectMethod: PartnerRedirectionMethod,
  val partnerName: String,
)
