package build.wallet.ui.components.icon

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.sheet.LocalSheetCloser
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.theme.WalletTheme
import kotlinx.coroutines.launch
import androidx.compose.material3.IconButton as MaterialIconButton

@Composable
fun IconButton(
  model: IconButtonModel,
  modifier: Modifier = Modifier,
) {
  when (model.iconModel.iconImage) {
    is LocalImage -> {
      val iconModel = model.iconModel
      val click: () -> Unit =
        when (model.onClick) {
          is StandardClick -> {
            { model.onClick.invoke() }
          }
          is SheetClosingClick -> {
            val scope = rememberStableCoroutineScope()
            val sheetCloser = LocalSheetCloser.current

            {
              scope.launch {
                sheetCloser()
              }.invokeOnCompletion { model.onClick() }
            }
          }
        }

      IconButton(
        modifier = modifier,
        iconModel = iconModel,
        enabled = model.enabled,
        text = iconModel.text,
        onClick = click
      )
    }
    else -> TODO("Handle other types of images")
  }
}

/**
 * A component for implementing clickable button that only contains an icon as the content.
 *
 * [iconBackgroundType] - determines what type of background to draw behind the
 * button's icon.
 */
@Composable
fun IconButton(
  iconModel: IconModel,
  modifier: Modifier = Modifier,
  iconColor: Color = Color.Unspecified,
  enabled: Boolean = true,
  text: String? = null,
  isClosingSheet: Boolean = false,
  onClick: () -> Unit,
) {
  val clickHandler: () -> Unit =
    if (isClosingSheet) {
      val scope = rememberStableCoroutineScope()
      val sheetCloser = LocalSheetCloser.current

      {
        scope.launch {
          sheetCloser()
        }.invokeOnCompletion { onClick() }
      }
    } else {
      onClick
    }
  val iconStyle =
    WalletTheme.iconStyle(
      icon = iconModel.iconImage,
      color = iconColor,
      tint = iconModel.iconTint
    )
  IconButton(
    modifier = modifier,
    iconModel = iconModel,
    text = text,
    enabled = enabled,
    color = iconStyle.color,
    onClick = clickHandler
  )
}

@Composable
fun IconButton(
  iconModel: IconModel,
  modifier: Modifier = Modifier,
  text: String? = null,
  enabled: Boolean = true,
  color: Color = Color.Unspecified,
  onClick: () -> Unit,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    when (iconModel.iconBackgroundType) {
      is IconBackgroundType.Square -> {
        FilledIconButton(
          modifier =
            modifier
              .size(iconModel.totalSize.dp)
              .alpha(if (enabled) 1f else 0.5f),
          shape = RoundedCornerShape((iconModel.iconBackgroundType as IconBackgroundType.Square).cornerRadius.dp),
          onClick = onClick
        ) {
          IconImage(
            model = iconModel,
            color =
              when {
                enabled -> color
                else -> WalletTheme.colors.foreground30
              }
          )
        }
      }
      else -> {
        MaterialIconButton(
          modifier =
            modifier
              .size(iconModel.totalSize.dp)
              .alpha(if (enabled) 1f else 0.5f),
          onClick = onClick
        ) {
          IconImage(
            model = iconModel,
            color =
              when {
                enabled -> color
                else -> WalletTheme.colors.foreground30
              }
          )
        }
      }
    }

    text?.let {
      Spacer(Modifier.height(8.dp))
      Label(
        text = it,
        treatment = if (enabled) LabelTreatment.Primary else LabelTreatment.Disabled
      )
    }
  }
}
