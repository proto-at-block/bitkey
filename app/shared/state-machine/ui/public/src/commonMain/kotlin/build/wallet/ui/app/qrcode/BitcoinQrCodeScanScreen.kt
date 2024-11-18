package build.wallet.ui.app.qrcode

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.send.QrCodeScanBodyModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.components.icon.iconStyle
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Unspecified
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.icon.*
import build.wallet.ui.system.BackHandler
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

private val qrCodeViewfinderMargin = 48.dp
private val qrCodeViewfinderBorderRadius = 40.dp

@Composable
fun QrCodeScanScreen(
  modifier: Modifier = Modifier,
  model: QrCodeScanBodyModel,
) {
  BackHandler(onBack = model.onClose)
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
  ) {
    NativeQrCodeScanner(model = model)
    QrCodeScanViewFinder()
    QrCodeScanWidgets(model = model)
  }
}

@Composable
internal expect fun NativeQrCodeScanner(model: QrCodeScanBodyModel)

@Composable
internal fun QrCodeScanViewFinder() {
  Canvas(modifier = Modifier.fillMaxSize()) {
    // the width of the view finder is the width of the canvas minus two sides of margin
    val viewFinderWidth = size.width - qrCodeViewfinderMargin.toPx() * 2

    with(drawContext.canvas.nativeCanvas) {
      val checkPoint = saveLayer(null, null)

      // Draw the transparent-black background
      drawRect(Color.Black.copy(alpha = 0.6f))

      // Cut out the QR code view finder from the black region
      drawRoundRect(
        color = Color.Transparent,
        blendMode = BlendMode.Clear,
        cornerRadius = CornerRadius(qrCodeViewfinderBorderRadius.toPx()),
        size =
          Size(
            width = viewFinderWidth,
            // the height is calculated to be the same as the width
            height = viewFinderWidth
          ),
        // offset the top left of the viewport
        topLeft =
          Offset(
            // offset by the intended margin on the x-axis
            x = qrCodeViewfinderMargin.toPx(),
            // offset by half the height minus half the height of the view finder
            y = size.height / 2 - viewFinderWidth / 2
          )
      )
      restoreToCount(checkPoint)
    }

    // Draw the white border on the cut out
    drawRoundRect(
      color = Color.White,
      style = Stroke(width = 3.dp.toPx()),
      cornerRadius = CornerRadius(qrCodeViewfinderBorderRadius.toPx()),
      // same size as the cutout
      size =
        Size(
          width = viewFinderWidth,
          height = viewFinderWidth
        ),
      // same offset as the cutout
      topLeft =
        Offset(
          x = qrCodeViewfinderMargin.toPx(),
          y = size.height / 2 - viewFinderWidth / 2
        )
    )
  }
}

@Composable
internal fun QrCodeScanWidgets(model: QrCodeScanBodyModel) {
  BoxWithConstraints(
    modifier =
      Modifier
        .fillMaxSize()
        .systemBarsPadding()
        .padding(20.dp)
  ) {
    Toolbar(
      modifier =
        Modifier
          .background(color = Color.Transparent)
          .align(Alignment.TopCenter),
      leadingContent = {
        IconButton(
          iconModel =
            IconModel(
              icon = Icon.SmallIconX,
              iconSize = IconSize.Accessory,
              iconTint = IconTint.OnTranslucent,
              iconBackgroundType =
                IconBackgroundType.Circle(
                  circleSize = IconSize.Regular,
                  color = IconBackgroundType.Circle.CircleColor.TranslucentBlack
                )
            ),
          color =
            WalletTheme.iconStyle(
              icon = IconImage.LocalImage(Icon.SmallIconX),
              color = Color.Unspecified,
              tint = IconTint.OnTranslucent
            ).color,
          onClick = model.onClose
        )
      },
      middleContent =
        model.headline?.let { headline ->
          {
            Label(
              text = headline,
              style =
                WalletTheme.labelStyle(
                  type = LabelType.Title2,
                  textColor = WalletTheme.colors.translucentForeground,
                  treatment = Unspecified
                )
            )
          }
        }
    )
    model.reticleLabel?.let { caption ->
      Label(
        // adjust label to lower text below view finder
        modifier =
          Modifier
            .align(Alignment.Center)
            .padding(top = maxWidth),
        text = caption,
        style =
          WalletTheme.labelStyle(
            type = LabelType.Body2Bold,
            textColor = WalletTheme.colors.translucentForeground,
            treatment = Unspecified
          )
      )
    }

    Column(
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
    ) {
      model.primaryButton?.let { Button(it) }
      model.secondaryButton?.let {
        Spacer(Modifier.size(16.dp))
        Button(it)
      }
    }
  }
}
