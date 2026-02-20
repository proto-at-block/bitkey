package build.wallet.ui.app.nfc

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.fwup.FwupNfcBodyModel
import build.wallet.statemachine.fwup.FwupNfcBodyModel.Status.*
import build.wallet.ui.app.LocalDeviceInfo
import build.wallet.ui.system.KeepScreenOn
import build.wallet.ui.tokens.LabelType

@Composable
fun FwupNfcScreen(
  modifier: Modifier = Modifier,
  model: FwupNfcBodyModel,
) {
  KeepScreenOn()
  when (LocalDeviceInfo.current.devicePlatform) {
    DevicePlatform.IOS -> {
      FwupNfcScreenInternalIos(model = model, modifier = modifier)
    }
    else -> {
      FwupNfcScreenInternal(model = model, modifier = modifier)
    }
  }
}

@Composable
internal fun FwupNfcScreenInternalIos(
  model: FwupNfcBodyModel,
  modifier: Modifier = Modifier,
) {
  NfcProgressScreenIosLayout(modifier = modifier) {
    when (model.status) {
      is Searching -> {
        NfcStatusLabel(
          text = "Ready to Update",
          labelType = LabelType.Title1
        )
        NfcStatusLabel(
          text = "Hold device to phone",
          labelType = LabelType.Body2Regular
        )
      }
      is InProgress -> {
        NfcStatusLabel(
          text = "Updating...",
          labelType = LabelType.Title1
        )
        NfcStatusLabel(
          text = "Continue holding to phone",
          labelType = LabelType.Body2Regular
        )
      }
      is LostConnection,
      is Success,
      -> Unit
    }
  }
}

@Composable
fun FwupNfcScreenInternal(
  model: FwupNfcBodyModel,
  modifier: Modifier = Modifier,
) {
  NfcProgressScreenAndroidLayout(
    modifier = modifier,
    onCancel = model.onCancel,
    statusIndicator = {
      NfcProgressStatusIndicator(
        statusState = FwupNfcStatusState(model.status)
      ) { status ->
        when (status) {
          is Searching ->
            NfcIcon()

          is InProgress ->
            NfcProgressPercentageLabel(
              progressText = status.progressText
            )

          is LostConnection ->
            NfcProgressPercentageLabel(
              progressText = "!",
              progressLabelType = LabelType.Display2
            )

          is Success ->
            NfcSuccessAnimation()
        }
      }
    },
    statusLabel = {
      NfcStatusLabel(
        text = model.status.text,
        animationLabel = "FwupNfcStatusText"
      )
    }
  )
}

/**
 * Status state adapter for FwupNfcBodyModel.Status.
 */
private data class FwupNfcStatusState(
  override val status: FwupNfcBodyModel.Status,
) : NfcProgressStatusState<FwupNfcBodyModel.Status> {
  override val progress: Float
    get() =
      when (status) {
        is InProgress -> status.progressPercentage
        is LostConnection -> status.progressPercentage
        else -> 0f
      }

  override val isIdle: Boolean
    get() = status is Searching

  override val isInProgress: Boolean
    get() = status is InProgress

  override val isSuccess: Boolean
    get() = status is Success

  override val isError: Boolean
    get() = status is LostConnection

  override fun shouldSkipTransition(
    old: FwupNfcBodyModel.Status,
    new: FwupNfcBodyModel.Status,
  ): Boolean {
    // Don't animate InProgress -> InProgress (just progress updates)
    return old is InProgress && new is InProgress
  }
}
