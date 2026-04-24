package build.wallet.ui.app.nfc

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bitkey.account.HardwareType
import bitkey.ui.Snapshot
import bitkey.ui.SnapshotHost
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.ios_nfc_background_standard
import bitkey.ui.framework_public.generated.resources.ios_nfc_background_w1
import build.wallet.platform.device.DeviceInfo
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcBodyModel
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcBodyModel.Status.*
import build.wallet.ui.app.LocalDeviceInfo
import build.wallet.ui.model.ComposeModel
import build.wallet.ui.system.KeepScreenOn
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.PreviewWalletTheme
import build.wallet.ui.tokens.LabelType
import org.jetbrains.compose.resources.painterResource

private val IosSignTransactionStatusContentOffset = (-8).dp
@Composable
fun SignTransactionNfcScreen(
  modifier: Modifier = Modifier,
  model: SignTransactionNfcBodyModel,
) {
  KeepScreenOn()
  val devicePlatform = LocalDeviceInfo.current.devicePlatform
  val designSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current

  when {
    devicePlatform == DevicePlatform.IOS -> {
      SignTransactionNfcScreenInternalIos(model = model, modifier = modifier)
    }
    devicePlatform == DevicePlatform.Android && designSystemV2Enabled -> {
      SignTransactionNfcScreenInternalV2(model = model, modifier = modifier)
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
  val designSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current

  if (model.shouldUseCustomBackgroundLayout()) {
    FwupSystemThemedContent(followIosSystemTheme = designSystemV2Enabled) {
      NfcProgressScreenIosLayout(
        modifier = modifier,
        hardwareType = model.hardwareType,
        backgroundColor = WalletTheme.colors.background,
        statusTopPadding = 40.dp,
        showDefaultHardwareBackground = false
      ) {
        SignTransactionNfcIosStatusContent(
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
    val backgroundDrawable = when (model.hardwareType) {
      HardwareType.W1 -> Res.drawable.ios_nfc_background_w1
      HardwareType.W3 -> Res.drawable.ios_nfc_background_standard
    }

    NfcProgressScreenIosLayout(
      modifier = modifier,
      hardwareType = model.hardwareType,
      backgroundColor = WalletTheme.colors.background,
      backgroundPainter =
        if (designSystemV2Enabled && !showDetailedIosInstructions) painterResource(backgroundDrawable) else null,
      backgroundTopPadding = 200.dp,
      statusTopPadding = if (designSystemV2Enabled) 40.dp else 48.dp,
      showDefaultHardwareBackground = !showDetailedIosInstructions
    ) {
      SignTransactionNfcIosStatusContent(
        status = model.status,
        designSystemV2Enabled = designSystemV2Enabled
      )
    }
  }
}

@Composable
private fun SignTransactionNfcIosStatusContent(
  status: SignTransactionNfcBodyModel.Status,
  designSystemV2Enabled: Boolean,
) {
  if (designSystemV2Enabled) {
    Column(
      modifier = Modifier.offset(y = IosSignTransactionStatusContentOffset),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      SignTransactionNfcIosStatusContentV2(status = status)
    }
  } else {
    SignTransactionNfcIosStatusContentLegacy(status = status)
  }
}

@Composable
private fun SignTransactionNfcIosStatusContentV2(status: SignTransactionNfcBodyModel.Status) {
  when (status) {
    is Searching -> {
      NfcStatusLabel(
        text = "Ready",
        labelType = LabelType.Body2MonoCaps,
        textColor = WalletTheme.colors.foreground
      )
      NfcStatusLabel(
        text = "Hold device to phone",
        labelType = LabelType.Body2Regular,
        textColor = WalletTheme.colors.foreground60
      )
    }
    is Signing -> {
      NfcStatusLabel(
        text = "Keep holding...",
        labelType = LabelType.Body2MonoCaps,
        textColor = WalletTheme.colors.foreground
      )
    }
    is Transferring -> {
      NfcStatusLabel(
        text = "Transferring...",
        labelType = LabelType.Body2MonoCaps,
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
private fun SignTransactionNfcIosStatusContentLegacy(status: SignTransactionNfcBodyModel.Status) {
  when (status) {
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
    is Signing -> {
      NfcStatusLabel(
        text = "This can take up to 1 minute…",
        labelType = LabelType.Title1
      )
      NfcStatusLabel(
        text = "Continue holding to phone",
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

@Composable
fun SignTransactionNfcScreenInternal(
  model: SignTransactionNfcBodyModel,
  modifier: Modifier = Modifier,
) {
  NfcProgressScreenAndroidLayout(
    modifier = modifier,
    onCancel = model.onCancel,
    statusIndicator = {
      SignTransactionNfcStatusIndicator(status = model.status)
    },
    statusLabel = {
      SignTransactionNfcStatusLabel(status = model.status)
    }
  )
}

@Composable
internal fun SignTransactionNfcScreenInternalV2(
  model: SignTransactionNfcBodyModel,
  modifier: Modifier = Modifier,
) {
  NfcProgressScreenAndroidLayoutV2(
    modifier = modifier,
    onCancel = model.onCancel,
    statusContent = {
      SignTransactionNfcStatusIndicator(status = model.status)
      SignTransactionNfcStatusLabel(
        status = model.status,
        modifier = Modifier.padding(top = 8.dp)
      )
    }
  )
}

@Composable
private fun SignTransactionNfcStatusIndicator(status: SignTransactionNfcBodyModel.Status) {
  NfcProgressStatusIndicator(
    statusState = SignTransactionNfcStatusState(status)
  ) { currentStatus ->
    when (currentStatus) {
      is Searching ->
        NfcIcon()

      is Signing ->
        NfcIcon()

      is Transferring ->
        NfcProgressPercentageLabel(
          progressText = "${(currentStatus.progress.value * 100).toInt()}%"
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
private fun SignTransactionNfcStatusLabel(
  status: SignTransactionNfcBodyModel.Status,
  modifier: Modifier = Modifier,
) {
  NfcStatusLabel(
    text = status.text,
    modifier = modifier,
    animationLabel = "SignTransactionNfcStatusText"
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
    get() = status is Signing || status is Transferring

  override val isIndeterminate: Boolean
    get() = status is Signing

  override val isSuccess: Boolean
    get() = status is Success

  override val isError: Boolean
    get() = status is LostConnection

  override fun shouldSkipTransition(
    old: SignTransactionNfcBodyModel.Status,
    new: SignTransactionNfcBodyModel.Status,
  ): Boolean {
    // Don't animate within the same in-progress state (just progress updates)
    return (old is Transferring && new is Transferring) ||
      (old is Signing && new is Signing)
  }
}

/**
 * Extension to get human-readable status text.
 */
private val SignTransactionNfcBodyModel.Status.text: String
  get() =
    when (this) {
      is Searching -> "Hold your Bitkey to the back of your phone"
      is Signing -> "This can take up to 1 minute…"
      is Transferring -> "Transferring transaction..."
      is LostConnection -> "Connection lost\nTap again to continue"
      is Success -> "Transaction signed"
    }

private val SignTransactionNfcBodyModel.Status.hasDetailedIosInstructions: Boolean
  get() =
    when (this) {
      is Searching, is Signing, is Transferring -> true
      is LostConnection, is Success -> false
    }

internal fun SignTransactionNfcBodyModel.shouldUseCustomBackgroundLayout(): Boolean =
  !showNativeSheetOnIos

internal fun SignTransactionNfcBodyModel.shouldFollowIosSystemTheme(
  designSystemV2Enabled: Boolean,
): Boolean =
  designSystemV2Enabled && showNativeSheetOnIos

internal fun SignTransactionNfcBodyModel.shouldShowDetailedIosInstructions(
  designSystemV2Enabled: Boolean,
): Boolean =
  designSystemV2Enabled &&
    showNativeSheetOnIos &&
    status.hasDetailedIosInstructions

@Snapshot
val SnapshotHost.signTransactionReady
  get() = signTransactionNfcSnapshotModel(status = Searching)

@Snapshot
val SnapshotHost.signTransactionKeepHolding
  get() = signTransactionNfcSnapshotModel(status = Signing)

@Snapshot
val SnapshotHost.signTransactionReadyCustomBackground
  get() = signTransactionNfcSnapshotModel(
    status = Searching,
    showNativeSheetOnIos = false
  )

@Snapshot
val SnapshotHost.signTransactionKeepHoldingCustomBackground
  get() = signTransactionNfcSnapshotModel(
    status = Signing,
    showNativeSheetOnIos = false
  )

private fun signTransactionNfcSnapshotModel(
  status: SignTransactionNfcBodyModel.Status,
  showNativeSheetOnIos: Boolean = true,
): SignTransactionNfcSnapshotModel {
  return SignTransactionNfcSnapshotModel(
    bodyModel =
      SignTransactionNfcBodyModel(
        onCancel = {},
        status = status,
        showNativeSheetOnIos = showNativeSheetOnIos,
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

data class SignTransactionNfcSnapshotModel(
  val bodyModel: SignTransactionNfcBodyModel,
  val deviceInfo: DeviceInfo,
) : ComposeModel {
  @Composable
  override fun render(modifier: Modifier) {
    PreviewWalletTheme(
      designSystemUpdatesEnabled = true
    ) {
      CompositionLocalProvider(
        LocalDeviceInfo provides deviceInfo,
        LocalDesignSystemUpdatesEnabled provides true
      ) {
        SignTransactionNfcScreen(
          modifier = modifier,
          model = bodyModel
        )
      }
    }
  }
}
