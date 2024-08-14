package build.wallet.statemachine.moneyhome.card.fwup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.ACTION_APP_TAP_FWUP_CARD
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.UpToDate
import build.wallet.fwup.FirmwareDataService
import build.wallet.statemachine.moneyhome.card.CardModel

class DeviceUpdateCardUiStateMachineImpl(
  private val eventTracker: EventTracker,
  private val firmwareDataService: FirmwareDataService,
) : DeviceUpdateCardUiStateMachine {
  @Composable
  override fun model(props: DeviceUpdateCardUiProps): CardModel? {
    val firmwareUpdateState = remember {
      firmwareDataService.firmwareData()
    }.collectAsState().value.firmwareUpdateState

    return when (firmwareUpdateState) {
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
