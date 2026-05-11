package build.wallet.ui.app.securityhub

import bitkey.ui.SnapshotHost
import bitkey.ui.screens.securityhub.completedRecommendations
import bitkey.ui.screens.securityhub.pendingRecommendations
import bitkey.ui.screens.securityhub.pendingRecommendationsWithFingerprintResetCard
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.model.render
import io.kotest.core.spec.style.FunSpec
import kotlinx.collections.immutable.toImmutableList

class SecurityHubScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Security Hub Screen all set") {
    paparazzi.snapshot {
      SnapshotHost.completedRecommendations.render()
    }
  }

  test("Security Hub Screen 3 recommendations") {
    paparazzi.snapshot {
      val model = SnapshotHost.pendingRecommendations
      model.copy(
        recommendations = model.recommendations.take(3).toImmutableList()
      ).render()
    }
  }

  test("Security Hub Screen 3 recommendations with design system v2 feature flag on") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      val model = SnapshotHost.pendingRecommendations
      model.copy(
        recommendations = model.recommendations.take(3).toImmutableList()
      ).render()
    }
  }

  test("Security Hub Screen with fingerprint reset card with design system v2 feature flag on") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      SnapshotHost.pendingRecommendationsWithFingerprintResetCard.render()
    }
  }
})
