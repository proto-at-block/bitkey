package build.wallet.statemachine.partnerships

import build.wallet.analytics.events.EventTrackerContext
import build.wallet.partnerships.PartnerInfo

class PartnerEventTrackerScreenIdContext(partnerInfo: PartnerInfo) : EventTrackerContext {
  override val name: String = partnerInfo.partnerId.value
}
