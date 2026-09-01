package build.wallet.partnerships

import build.wallet.platform.random.uuid

/**
 * Request to create a transfer link for a partner integration.
 *
 * [requestId] is a unique identifier generated for each deep link invocation.
 * This ensures that repeated deep links with the same parameters (e.g., user cancels
 * and retries from the partner app) are treated as distinct requests, forcing
 * Compose's `remember` to recompute state.
 */
data class PartnerTransferLinkRequest(
  val partner: String,
  val event: String,
  val eventId: String,
  val requestId: String = uuid(),
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
