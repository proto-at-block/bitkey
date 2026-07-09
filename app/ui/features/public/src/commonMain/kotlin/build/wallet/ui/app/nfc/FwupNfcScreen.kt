package build.wallet.ui.app.nfc

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import bitkey.ui.Snapshot
import bitkey.ui.SnapshotHost
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.ios_nfc_background_fwup
import bitkey.ui.framework_public.generated.resources.ios_nfc_background_fwup_light
import build.wallet.platform.device.DeviceInfo
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.fwup.FwupNfcBodyModel
import build.wallet.statemachine.fwup.FwupNfcBodyModel.Status.*
import build.wallet.ui.app.LocalDeviceInfo
import build.wallet.ui.model.ComposeModel
import build.wallet.ui.system.KeepScreenOn
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import org.jetbrains.compose.resources.painterResource

private val IosFwupStatusContentOffset = (-8).dp

@Composable
fun FwupNfcScreen(
  modifier: Modifier = Modifier,
  model: FwupNfcBodyModel,
) {
  KeepScreenOn()
  val devicePlatform = LocalDeviceInfo.current.devicePlatform

  when (devicePlatform) {
    DevicePlatform.IOS -> {
      FwupNfcScreenInternalIos(model = model, modifier = modifier)
    }
    DevicePlatform.Android,
    DevicePlatform.Jvm,
    -> {
      FwupNfcScreenInternalAndroid(model = model, modifier = modifier)
    }
  }
}

@Composable
internal fun FwupNfcScreenInternalIos(
  model: FwupNfcBodyModel,
  modifier: Modifier = Modifier,
  backgroundPainter: Painter? = null,
) {
  if (!model.showNativeSheetOnIos) {
    FwupSystemThemedContent(followIosSystemTheme = true) {
      NfcProgressScreenIosLayout(
        modifier = modifier,
        backgroundColor = WalletTheme.colors.background,
        statusTopPadding = 40.dp,
        showDefaultHardwareBackground = false
      ) {
        FwupNfcIosOffsetStatusContent(
          status = model.status
        )
      }
    }
    return
  }

  FwupSystemThemedContent(
    followIosSystemTheme = true
  ) {
    val showDetailedIosInstructions = model.status.hasDetailedIosInstructions
    val resolvedBackgroundPainter =
      fwupIosBackgroundPainter(
        showDetailedIosInstructions = showDetailedIosInstructions,
        backgroundPainter = backgroundPainter
      )

    NfcProgressScreenIosLayout(
      modifier = modifier,
      backgroundColor = WalletTheme.colors.background,
      backgroundPainter = resolvedBackgroundPainter,
      backgroundTopPadding = 200.dp,
      statusTopPadding = 40.dp,
      showDefaultHardwareBackground = !showDetailedIosInstructions
    ) {
      FwupNfcIosOffsetStatusContent(
        status = model.status
      )
    }
  }
}

@Composable
private fun fwupIosBackgroundPainter(
  showDetailedIosInstructions: Boolean,
  backgroundPainter: Painter?,
): Painter? {
  val theme = LocalTheme.current
  val backgroundDrawable = when (theme) {
    Theme.DARK -> Res.drawable.ios_nfc_background_fwup
    Theme.LIGHT -> Res.drawable.ios_nfc_background_fwup_light
  }

  return if (!showDetailedIosInstructions) {
    backgroundPainter ?: painterResource(backgroundDrawable)
  } else {
    backgroundPainter
  }
}

@Composable
private fun FwupNfcIosOffsetStatusContent(
  status: FwupNfcBodyModel.Status,
) {
  Column(
    modifier = Modifier.offset(y = IosFwupStatusContentOffset),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    FwupNfcIosStatusContent(status = status)
  }
}

@Composable
private fun FwupNfcIosStatusContent(status: FwupNfcBodyModel.Status) {
  when (status) {
    is Searching -> {
      NfcStatusLabel(
        text = "Ready to Update",
        labelType = LabelType.Body1Mono,
        textColor = WalletTheme.colors.foreground
      )
      NfcStatusLabel(
        text = "Hold device to phone",
        labelType = LabelType.Body2Regular,
        textColor = WalletTheme.colors.foreground60
      )
    }
    is InProgress -> {
      NfcStatusLabel(
        text = "Updating...",
        labelType = LabelType.Body1Mono,
        textColor = WalletTheme.colors.foreground
      )
      NfcStatusLabel(
        text = "Continue holding to phone",
        labelType = LabelType.Body2Regular,
        textColor = WalletTheme.colors.foreground60
      )
    }
    is LostConnection,
    is Success,
    -> Unit
  }
}

@Composable
internal fun FwupNfcScreenInternalAndroid(
  model: FwupNfcBodyModel,
  modifier: Modifier = Modifier,
) {
  NfcProgressScreenAndroidLayout(
    modifier = modifier,
    onCancel = model.onCancel,
    // Disable back gesture during firmware update - use cancel button instead
    enableBackGesture = false,
    statusContent = {
      FwupNfcStatusIndicator(model.status)

      FwupNfcStatusLabel(
        text = model.status.text,
        modifier = Modifier.padding(top = 8.dp)
      )
    }
  )
}

@Composable
private fun FwupNfcStatusIndicator(status: FwupNfcBodyModel.Status) {
  NfcProgressStatusIndicator(
    statusState = FwupNfcStatusState(status)
  ) { currentStatus ->
    when (currentStatus) {
      is Searching ->
        NfcIcon()

      is InProgress ->
        NfcProgressPercentageLabel(
          progressText = currentStatus.progressText,
          progressLabelType = LabelType.Title3
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
}

@Composable
private fun FwupNfcStatusLabel(
  text: String,
  modifier: Modifier = Modifier,
) {
  NfcStatusLabel(
    text = text,
    modifier = modifier,
    animationLabel = "FwupNfcStatusText"
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

private val FwupNfcBodyModel.Status.hasDetailedIosInstructions: Boolean
  get() =
    when (this) {
      is Searching, is InProgress -> true
      is LostConnection, is Success -> false
    }

@Snapshot
val SnapshotHost.fwupNfcReadyToUpdate
  get() = fwupNfcSnapshotModel(status = Searching())

@Snapshot
val SnapshotHost.fwupNfcUpdating
  get() = fwupNfcSnapshotModel(status = InProgress(fwupProgress = 33f))

private fun fwupNfcSnapshotModel(
  status: FwupNfcBodyModel.Status,
): FwupNfcSnapshotModel {
  return FwupNfcSnapshotModel(
    bodyModel =
      FwupNfcBodyModel(
        onCancel = {},
        status = status,
        showNativeSheetOnIos = false,
        eventTrackerScreenInfo = null
      ),
    deviceInfo =
      DeviceInfo(
        deviceModel = "iPhone17,1",
        devicePlatform = DevicePlatform.IOS,
        isEmulator = true
      )
  )
}

data class FwupNfcSnapshotModel(
  val bodyModel: FwupNfcBodyModel,
  val deviceInfo: DeviceInfo,
) : ComposeModel {
  @Composable
  override fun render(modifier: Modifier) {
    CompositionLocalProvider(
      LocalDeviceInfo provides deviceInfo
    ) {
      FwupNfcScreen(
        modifier = modifier,
        model = bodyModel
      )
    }
  }
}
