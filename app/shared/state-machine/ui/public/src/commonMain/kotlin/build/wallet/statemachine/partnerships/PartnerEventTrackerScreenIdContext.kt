package build.wallet.statemachine.partnerships

import build.wallet.analytics.events.screen.context.EventTrackerScreenIdContext
import build.wallet.f8e.partnerships.PartnerInfo

class PartnerEventTrackerScreenIdContext(partnerInfo: PartnerInfo) : EventTrackerScreenIdContext {
  override val name: String = partnerInfo.partner
}
