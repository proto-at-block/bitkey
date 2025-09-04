package build.wallet.analytics.events.screen.id

enum class PartnershipsEventTrackerScreenId : EventTrackerScreenId {
  /** Loading screen shown when retrieving partner info. */
  PARTNER_TRANSFER_LINK_RETRIEVING_PARTNER_INFO,

  /** Screen showing confirmation for creating a partner transfer link */
  PARTNER_TRANSFER_LINK_CONFIRMATION,

  /** Loading screen shown while processing partner transfer link request */
  PARTNER_TRANSFER_LINK_PROCESSING,

  /** Error screen shown when partner transfer link fails and cannot be retried */
  PARTNER_TRANSFER_LINK_UNRETRYABLE_ERROR,

  /** Error screen shown when partner transfer link fails and can be retried */
  PARTNER_TRANSFER_LINK_RETRYABLE_ERROR,

  /** Error screen shown when the redirect back to the partner fails. */
  PARTNER_TRANSFER_REDIRECT_ERROR,

  /** Error screen shown then the partner cannot be found. */
  PARTNER_TRANSFER_PARTNER_NOT_FOUND_ERROR,
}
