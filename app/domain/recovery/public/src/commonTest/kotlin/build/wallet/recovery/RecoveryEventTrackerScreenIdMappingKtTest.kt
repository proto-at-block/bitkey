package build.wallet.recovery

import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_DDK_LOADING_ERROR
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_INITIATION_AUTHENTICATING_WITH_F8E
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_INITIATION_AWAITING_AUTH_CHALLENGE
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_LISTING_KEYSETS
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.core.spec.style.FunSpec

class RecoveryEventTrackerScreenIdMappingKtTest : FunSpec({
  /**
   * We want to make sure that all LOST_APP_XXX events have a corresponding
   * LOST_HW_XXX event except where it doesn't make sense.
   */
  test("enums values have parity across app and hardware") {
    val excluded =
      listOf(
        // These screens don't exist for lost hardware recovery
        LOST_APP_DELAY_NOTIFY_INITIATION_AWAITING_AUTH_CHALLENGE,
        LOST_APP_DELAY_NOTIFY_INITIATION_AUTHENTICATING_WITH_F8E,
        LOST_APP_DELAY_NOTIFY_LISTING_KEYSETS,
        LOST_APP_DELAY_NOTIFY_DDK_LOADING_ERROR
      )
    DelayNotifyRecoveryEventTrackerScreenId.entries
      .filter { !excluded.contains(it) }
      .forEach {
        assertSoftly {
          shouldNotThrow<Exception> {
            HardwareRecoveryEventTrackerScreenId.valueOf(
              it.name.replace("LOST_APP", "LOST_HW")
            )
          }
        }
      }
  }
})
