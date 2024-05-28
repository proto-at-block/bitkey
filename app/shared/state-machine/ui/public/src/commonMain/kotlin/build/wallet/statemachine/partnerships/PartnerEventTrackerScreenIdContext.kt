package build.wallet.statemachine.partnerships

import build.wallet.analytics.events.screen.context.EventTrackerScreenIdContext
import build.wallet.partnerships.PartnerInfo

class PartnerEventTrackerScreenIdContext(partnerInfo: PartnerInfo) : EventTrackerScreenIdContext {
  override val name: String = partnerInfo.partnerId.value
}
