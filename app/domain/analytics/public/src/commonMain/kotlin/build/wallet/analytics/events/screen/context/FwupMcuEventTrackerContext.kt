package build.wallet.analytics.events.screen.context

import build.wallet.analytics.events.EventTrackerContext

/**
 * Context for FWUP events that are specific to a particular MCU.
 * The context name (CORE or UXC) is included in the event's screen_id.
 */
enum class FwupMcuEventTrackerContext : EventTrackerContext {
  CORE,
  UXC,
}
