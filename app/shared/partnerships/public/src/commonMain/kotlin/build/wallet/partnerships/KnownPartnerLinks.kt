package build.wallet.partnerships

/**
 * A set of known associations between partner application IDs and their
 * deep links for showing transaction information.
 *
 * Note: This is intended to be a temporary solution and should be removed
 * in favor of a server-provided value in the future.
 */
enum class KnownPartnerLinks(
  val id: PartnerId,
  val transactionRedirectLink: PartnerRedirectionMethod,
) {
  CashApp(
    id = PartnerId("CashApp"),
    transactionRedirectLink = PartnerRedirectionMethod.Deeplink(
      urlString = "cashme://cash.app/launch/activity",
      appRestrictions = null,
      partnerName = "Cash App"
    )
  ),
  Coinbase(
    id = PartnerId("Coinbase"),
    transactionRedirectLink = PartnerRedirectionMethod.Web(
      urlString = "https://coinbase.com"
    )
  ),
}

/**
 * Get a deep link for a partner info object, if known.
 */
val PartnerInfo.transactionDeepLink get() = KnownPartnerLinks.entries.find {
  it.id.value == partner
}?.transactionRedirectLink
