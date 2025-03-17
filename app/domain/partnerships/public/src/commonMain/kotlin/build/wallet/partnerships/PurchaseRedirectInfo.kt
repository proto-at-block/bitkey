package build.wallet.partnerships

/**
 * Represents redirect info for completing purchasing of bitcoin.
 *
 * @param redirectMethod how redirect should be executed, see [PartnerRedirectionMethod].
 * @param
 */
data class PurchaseRedirectInfo(
  val redirectMethod: PartnerRedirectionMethod,
  val transaction: PartnershipTransaction,
)
