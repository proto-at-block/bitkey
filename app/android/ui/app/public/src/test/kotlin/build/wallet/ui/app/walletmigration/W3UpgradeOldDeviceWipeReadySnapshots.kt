package build.wallet.ui.app.walletmigration

import bitkey.ui.statemachine.interstitial.W3UpgradeOldDeviceWipeReadyBodyModel
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.app.paparazzi.snapshotSheet
import io.kotest.core.spec.style.FunSpec

class W3UpgradeOldDeviceWipeReadySnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("W3 upgrade old device wipe ready sheet") {
    paparazzi.snapshotSheet(
      model = W3UpgradeOldDeviceWipeReadyBodyModel(
        onWipeOldDevice = {},
        onDone = {}
      )
    )
  }
})
