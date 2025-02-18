package bitkey.ui.screens.demo

import build.wallet.analytics.events.screen.id.EventTrackerScreenId

enum class DemoCodeTrackerScreenId : EventTrackerScreenId {
  /** Screen to enter a code for demo mode */
  DEMO_CODE_CONFIG,
  DEMO_MODE_CODE_ENTRY,
  DEMO_MODE_CODE_SUBMISSION,
}
