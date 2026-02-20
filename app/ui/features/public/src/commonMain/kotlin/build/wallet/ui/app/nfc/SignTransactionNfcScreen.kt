package build.wallet.ui.app.nfc

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcBodyModel
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcBodyModel.Status.*
import build.wallet.ui.app.LocalDeviceInfo
import build.wallet.ui.system.KeepScreenOn
import build.wallet.ui.tokens.LabelType

@Composable
fun SignTransactionNfcScreen(
  modifier: Modifier = Modifier,
  model: SignTransactionNfcBodyModel,
) {
  KeepScreenOn()
  when (LocalDeviceInfo.current.devicePlatform) {
    DevicePlatform.IOS -> {
      SignTransactionNfcScreenInternalIos(model = model, modifier = modifier)
    }
    else -> {
      SignTransactionNfcScreenInternal(model = model, modifier = modifier)
    }
  }
}

@Composable
internal fun SignTransactionNfcScreenInternalIos(
  model: SignTransactionNfcBodyModel,
  modifier: Modifier = Modifier,
) {
  NfcProgressScreenIosLayout(modifier = modifier) {
    when (model.status) {
      is Searching -> {
        NfcStatusLabel(
          text = "Ready to Sign",
          labelType = LabelType.Title1
        )
        NfcStatusLabel(
          text = "Hold device to phone",
          labelType = LabelType.Body2Regular
        )
      }
      is Transferring -> {
        NfcStatusLabel(
          text = "Transferring...",
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
fun SignTransactionNfcScreenInternal(
  model: SignTransactionNfcBodyModel,
  modifier: Modifier = Modifier,
) {
  NfcProgressScreenAndroidLayout(
    modifier = modifier,
    onCancel = model.onCancel,
    statusIndicator = {
      NfcProgressStatusIndicator(
        statusState = SignTransactionNfcStatusState(model.status)
      ) { status ->
        when (status) {
          is Searching ->
            NfcIcon()

          is Transferring ->
            NfcProgressPercentageLabel(
              progressText = "${(status.progress.value * 100).toInt()}%"
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
        animationLabel = "SignTransactionNfcStatusText"
      )
    }
  )
}

/**
 * Status state adapter for SignTransactionNfcBodyModel.Status.
 */
private data class SignTransactionNfcStatusState(
  override val status: SignTransactionNfcBodyModel.Status,
) : NfcProgressStatusState<SignTransactionNfcBodyModel.Status> {
  override val progress: Float
    get() =
      when (status) {
        is Transferring -> status.progress.value
        is LostConnection -> status.progress.value
        else -> 0f
      }

  override val isIdle: Boolean
    get() = status is Searching

  override val isInProgress: Boolean
    get() = status is Transferring

  override val isSuccess: Boolean
    get() = status is Success

  override val isError: Boolean
    get() = status is LostConnection

  override fun shouldSkipTransition(
    old: SignTransactionNfcBodyModel.Status,
    new: SignTransactionNfcBodyModel.Status,
  ): Boolean {
    // Don't animate Transferring -> Transferring (just progress updates)
    return old is Transferring && new is Transferring
  }
}

/**
 * Extension to get human-readable status text.
 */
private val SignTransactionNfcBodyModel.Status.text: String
  get() =
    when (this) {
      is Searching -> "Hold device here behind phone"
      is Transferring -> "Transferring transaction..."
      is LostConnection -> "Connection lost\nTap again to continue"
      is Success -> "Transaction signed"
    }
