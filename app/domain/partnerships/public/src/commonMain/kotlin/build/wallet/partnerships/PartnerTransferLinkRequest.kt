package build.wallet.partnerships

/**
 * Request to create a transfer link for a partner integration
 */
data class PartnerTransferLinkRequest(
  val partner: String,
  val event: String,
  val eventId: String,
) {
  companion object {
    fun fromRouteParams(
      partner: String?,
      event: String?,
      eventId: String?,
    ): PartnerTransferLinkRequest? {
      val validPartner = partner ?: return null
      val validEvent = event ?: return null
      val validEventId = eventId ?: return null

      return PartnerTransferLinkRequest(
        partner = validPartner,
        event = validEvent,
        eventId = validEventId
      )
    }
  }
}
