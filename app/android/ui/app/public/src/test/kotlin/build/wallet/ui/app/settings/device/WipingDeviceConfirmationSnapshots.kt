package build.wallet.ui.app.settings.device

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.settings.full.device.wipedevice.confirmation.WipingDeviceConfirmationBodyModel
import build.wallet.statemachine.settings.full.device.wipedevice.confirmation.WipingDeviceConfirmationItemModel
import build.wallet.statemachine.settings.full.device.wipedevice.confirmation.WipingDeviceConfirmationState
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec
import kotlinx.collections.immutable.toImmutableList

class WipingDeviceConfirmationSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("wipe device confirmation - unchecked") {
    paparazzi.snapshot {
      FormScreen(
        wipingDeviceConfirmationBodyModel(
          state = WipingDeviceConfirmationState.NotCompleted,
          isConfirmEnabled = false
        )
      )
    }
  }

  test("wipe device confirmation - checked") {
    paparazzi.snapshot {
      FormScreen(
        wipingDeviceConfirmationBodyModel(
          state = WipingDeviceConfirmationState.Completed,
          isConfirmEnabled = true
        )
      )
    }
  }
})

private fun wipingDeviceConfirmationBodyModel(
  state: WipingDeviceConfirmationState,
  isConfirmEnabled: Boolean,
) = WipingDeviceConfirmationBodyModel(
  onBack = {},
  onConfirmWipeDevice = {},
  messageItemModels = confirmationMessages.map { message ->
    WipingDeviceConfirmationItemModel(
      state = state,
      title = message,
      onClick = {}
    )
  }.toImmutableList(),
  isConfirmEnabled = isConfirmEnabled
)

private val confirmationMessages = listOf(
  "Wiping disconnects this device from your Bitkey wallet.",
  "This device will no longer access the funds in your wallet.",
  "This device will no longer help recover your wallet.",
  "After the wipe is complete, you can safely give away or dispose of this device."
)
