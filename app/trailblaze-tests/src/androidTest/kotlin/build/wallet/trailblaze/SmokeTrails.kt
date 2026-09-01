package build.wallet.trailblaze

import org.junit.Rule
import org.junit.Test
import xyz.block.trailblaze.android.AndroidTrailblazeRule

/**
 * Smoke-level UI tests replayed from recorded trails bundled as test-APK assets.
 *
 * These run against the separately installed debug app (world.bitkey.debug), which defaults to
 * Staging F8e + SIGNET + fake hardware. In CI they replay recordings only — pass
 * `trailblaze.aiEnabled=false` as an instrumentation argument so no LLM is consulted.
 *
 * Trail authoring workflow: docs/docs/mobile/testing/trailblaze.md
 */
class SmokeTrails {
  @get:Rule
  val rule = AndroidTrailblazeRule()

  @Test
  fun welcomeScreen() = rule.runFromAsset("bitkey/smoke/welcome-screen/android-phone.trail.yaml")

  @Test
  fun newWalletOnboarding() =
    rule.runFromAsset("bitkey/smoke/new-wallet-onboarding/android-phone.trail.yaml")
}
