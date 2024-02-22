package build.wallet.statemachine.moneyhome.card.fwup

import androidx.compose.runtime.Composable
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.ACTION_APP_TAP_FWUP_CARD
import build.wallet.statemachine.data.firmware.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.statemachine.data.firmware.FirmwareData.FirmwareUpdateState.UpToDate
import build.wallet.statemachine.moneyhome.card.CardModel

class DeviceUpdateCardUiStateMachineImpl(
  private val eventTracker: EventTracker,
) : DeviceUpdateCardUiStateMachine {
  @Composable
  override fun model(props: DeviceUpdateCardUiProps): CardModel? {
    return when (val firmwareUpdateState = props.firmwareData.firmwareUpdateState) {
      is UpToDate -> null
      is PendingUpdate ->
        DeviceUpdateCardModel(
          onUpdateDevice = {
            eventTracker.track(ACTION_APP_TAP_FWUP_CARD)
            props.onUpdateDevice(firmwareUpdateState)
          }
        )
    }
  }
}
