package build.wallet.statemachine.dev

import build.wallet.analytics.events.screen.id.EventTrackerScreenId

enum class DebugMenuEventTrackerScreenId : EventTrackerScreenId {
  /** Screen for entering a custom F8e server URL */
  F8E_CUSTOM_URL_ENTRY,

  /** Error screen shown when a debug menu operation fails */
  DEBUG_MENU_ERROR,
}
