package build.wallet.ui.app.nfc

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
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
  val designSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current

  when {
    devicePlatform == DevicePlatform.IOS -> {
      FwupNfcScreenInternalIos(model = model, modifier = modifier)
    }
    devicePlatform == DevicePlatform.Android && designSystemV2Enabled -> {
      FwupNfcScreenInternalV2(model = model, modifier = modifier)
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
  backgroundPainter: Painter? = null,
) {
  val designSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current

  if (!model.showNativeSheetOnIos) {
    FwupSystemThemedContent(followIosSystemTheme = designSystemV2Enabled) {
      NfcProgressScreenIosLayout(
        modifier = modifier,
        backgroundColor = WalletTheme.colors.background,
        statusTopPadding = 40.dp,
        showDefaultHardwareBackground = false
      ) {
        FwupNfcIosStatusContent(
          status = model.status,
          designSystemV2Enabled = designSystemV2Enabled
        )
      }
    }
    return
  }

  FwupSystemThemedContent(
    followIosSystemTheme = model.shouldFollowIosSystemTheme(designSystemV2Enabled)
  ) {
    val showDetailedIosInstructions = model.shouldShowDetailedIosInstructions(designSystemV2Enabled)
    val resolvedBackgroundPainter =
      fwupIosBackgroundPainter(
        designSystemV2Enabled = designSystemV2Enabled,
        showDetailedIosInstructions = showDetailedIosInstructions,
        backgroundPainter = backgroundPainter
      )

    NfcProgressScreenIosLayout(
      modifier = modifier,
      backgroundColor = WalletTheme.colors.background,
      backgroundPainter = resolvedBackgroundPainter,
      backgroundTopPadding = 200.dp,
      statusTopPadding = if (designSystemV2Enabled) 40.dp else 48.dp,
      showDefaultHardwareBackground = !showDetailedIosInstructions
    ) {
      FwupNfcIosStatusContent(
        status = model.status,
        designSystemV2Enabled = designSystemV2Enabled
      )
    }
  }
}

@Composable
private fun fwupIosBackgroundPainter(
  designSystemV2Enabled: Boolean,
  showDetailedIosInstructions: Boolean,
  backgroundPainter: Painter?,
): Painter? {
  val theme = LocalTheme.current
  val backgroundDrawable = when (theme) {
    Theme.DARK -> Res.drawable.ios_nfc_background_fwup
    Theme.LIGHT -> Res.drawable.ios_nfc_background_fwup_light
  }

  return if (designSystemV2Enabled && !showDetailedIosInstructions) {
    backgroundPainter ?: painterResource(backgroundDrawable)
  } else {
    backgroundPainter
  }
}

@Composable
private fun FwupNfcIosStatusContent(
  status: FwupNfcBodyModel.Status,
  designSystemV2Enabled: Boolean,
) {
  if (designSystemV2Enabled) {
    Column(
      modifier = Modifier.offset(y = IosFwupStatusContentOffset),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      FwupNfcIosStatusContentV2(status = status)
    }
  } else {
    FwupNfcIosStatusContentLegacy(status = status)
  }
}

@Composable
private fun FwupNfcIosStatusContentV2(status: FwupNfcBodyModel.Status) {
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
private fun FwupNfcIosStatusContentLegacy(status: FwupNfcBodyModel.Status) {
  when (status) {
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

@Composable
fun FwupNfcScreenInternal(
  model: FwupNfcBodyModel,
  modifier: Modifier = Modifier,
) {
  NfcProgressScreenAndroidLayout(
    modifier = modifier,
    onCancel = model.onCancel,
    // Disable back gesture during firmware update - use cancel button instead
    enableBackGesture = false,
    statusIndicator = {
      FwupNfcStatusIndicator(model.status)
    },
    statusLabel = {
      FwupNfcStatusLabel(text = model.status.text)
    }
  )
}

@Composable
internal fun FwupNfcScreenInternalV2(
  model: FwupNfcBodyModel,
  modifier: Modifier = Modifier,
) {
  NfcProgressScreenAndroidLayoutV2(
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
  val designSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
  NfcProgressStatusIndicator(
    statusState = FwupNfcStatusState(status)
  ) { currentStatus ->
    when (currentStatus) {
      is Searching ->
        NfcIcon()

      is InProgress ->
        NfcProgressPercentageLabel(
          progressText = currentStatus.progressText,
          progressLabelType = if (designSystemV2Enabled) LabelType.Title3 else LabelType.Title1
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

internal fun FwupNfcBodyModel.shouldFollowIosSystemTheme(
  designSystemV2Enabled: Boolean,
): Boolean =
  designSystemV2Enabled && showNativeSheetOnIos

private fun FwupNfcBodyModel.shouldShowDetailedIosInstructions(
  designSystemV2Enabled: Boolean,
): Boolean =
  designSystemV2Enabled &&
    showNativeSheetOnIos &&
    status.hasDetailedIosInstructions

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
      LocalDeviceInfo provides deviceInfo,
      LocalDesignSystemUpdatesEnabled provides true
    ) {
      FwupNfcScreen(
        modifier = modifier,
        model = bodyModel
      )
    }
  }
}
