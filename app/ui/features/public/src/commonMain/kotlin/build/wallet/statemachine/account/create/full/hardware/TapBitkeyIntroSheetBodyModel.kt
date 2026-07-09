package build.wallet.statemachine.account.create.full.hardware

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.LEADING
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.SMALL
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationHelpContent.Companion.TapBitkey
import build.wallet.statemachine.send.hardwareconfirmation.TapBitkeyPlacementVideo
import build.wallet.ui.app.core.form.FooterContent
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.sheet.SheetCornerRadius
import build.wallet.ui.components.video.VideoScalingMode
import build.wallet.ui.model.ComposeModel
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.WalletTheme

class TapBitkeyIntroSheetBodyModel(
  onDismiss: () -> Unit,
  onLearnMore: () -> Unit,
  onHasNoScreen: (() -> Unit)?,
  private val devicePlatform: DevicePlatform,
) : FormBodyModel(
    id = null,
    onBack = onDismiss,
    toolbar = null,
    header = sheetHeader(devicePlatform, onLearnMore),
    primaryButton = ButtonModel(
      text = "Got it",
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = SheetClosingClick(onDismiss)
    ),
    secondaryButton = onHasNoScreen?.let {
      ButtonModel(
        text = "My Bitkey doesn't have a screen",
        treatment = ButtonModel.Treatment.Secondary,
        size = ButtonModel.Size.Footer,
        onClick = StandardClick(it)
      )
    },
    renderContext = Sheet
  ) {
  @Composable
  override fun render(modifier: Modifier) {
    Column(
      modifier = modifier
        .fillMaxWidth()
        .background(WalletTheme.colors.background)
        .verticalScroll(rememberScrollState())
    ) {
      TapBitkeyIntroVideoContentModel(devicePlatform = devicePlatform).render(Modifier.fillMaxWidth())

      Column(
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 28.dp)
      ) {
        header?.let {
          Header(
            model = it,
            headlineLabelType = it.headlineLabelType
          )
        }

        Spacer(modifier = Modifier.height(24.dp))

        FooterContent(
          primaryButton = primaryButton,
          secondaryButton = secondaryButton,
          tertiaryButton = tertiaryButton
        )
      }
    }
  }

  companion object {
    private fun sheetHeader(
      devicePlatform: DevicePlatform,
      onLearnMore: () -> Unit,
    ) = FormHeaderModel(
      headline = "How to tap your Bitkey",
      sublineModel = LabelModel.LinkSubstringModel.from(
        string = "${introSubtitle(devicePlatform)} Learn more",
        substringToOnClick = mapOf("Learn more" to onLearnMore),
        underline = true,
        bold = true,
        color = LabelModel.Color.INVERSE
      ),
      iconModel = null,
      alignment = LEADING,
      sublineTreatment = SMALL
    )

    private fun introSubtitle(devicePlatform: DevicePlatform): String =
      when (devicePlatform) {
        DevicePlatform.IOS ->
          "Tap your Bitkey along the top edge of your phone with the screen side of the device facing the back of your phone."
        DevicePlatform.Android,
        DevicePlatform.Jvm,
        ->
          "Tap your Bitkey with the screen side of your device facing the back of your phone."
      }
  }
}

private data class TapBitkeyIntroVideoContentModel(
  private val devicePlatform: DevicePlatform,
) : ComposeModel {
  @Composable
  override fun render(modifier: Modifier) {
    val videoResourceName = TapBitkey
      .videoResourceName(devicePlatform, LocalTheme.current)

    if (videoResourceName == null) return

    Box(
      modifier = modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .clip(
          RoundedCornerShape(
            topStart = SheetCornerRadius,
            topEnd = SheetCornerRadius
          )
        )
        .background(WalletTheme.colors.foreground10)
    ) {
      TapBitkeyPlacementVideo(
        modifier = Modifier.fillMaxSize(),
        backgroundColor = WalletTheme.colors.foreground10,
        videoResourceName = videoResourceName,
        scalingMode = VideoScalingMode.CROP,
        topCornerRadius = SheetCornerRadius
      )
    }
  }
}
