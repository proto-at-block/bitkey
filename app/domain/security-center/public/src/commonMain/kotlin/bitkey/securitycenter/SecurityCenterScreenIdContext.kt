package bitkey.securitycenter

import build.wallet.analytics.events.EventTrackerContext

data class SecurityCenterScreenIdContext(val securityActionRecommendation: SecurityActionRecommendation, val isPending: Boolean) : EventTrackerContext {
  override val name: String = "${securityActionRecommendation.name}_${if (isPending) "PENDING" else "COMPLETED"}"
}
