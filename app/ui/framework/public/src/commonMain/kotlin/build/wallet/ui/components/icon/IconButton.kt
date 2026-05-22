package build.wallet.ui.components.icon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.sheet.LocalSheetCloser
import build.wallet.ui.compose.iconButtonTestTag
import build.wallet.ui.compose.resId
import build.wallet.ui.compose.resolveTestTag
import build.wallet.ui.compose.scalingClickable
import build.wallet.ui.compose.testTagDescriptor
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconImage.MarketIconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import kotlinx.coroutines.launch

@Composable
fun IconButton(
  model: IconButtonModel,
  modifier: Modifier = Modifier,
  testTag: String? = null,
) {
  when (model.iconModel.iconImage) {
    is LocalImage,
    is MarketIconImage,
    -> {
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
        testTag = resolveTestTag(
          testTag = testTag ?: model.testTag,
          fallbackTag = iconButtonTestTag(text = iconModel.text, iconDescriptor = iconModel.testTagDescriptor())
        ),
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
  testTag: String? = null,
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
    testTag = testTag,
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
  testTag: String? = null,
  color: Color = Color.Unspecified,
  onClick: () -> Unit,
) {
  val resolvedTestTag = resolveTestTag(
    testTag = testTag,
    fallbackTag =
      iconButtonTestTag(
        text = text,
        iconDescriptor = iconModel.testTagDescriptor()
      )
  )

  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    when (iconModel.iconBackgroundType) {
      is IconBackgroundType.Square -> {
        val backgroundType = iconModel.iconBackgroundType as IconBackgroundType.Square
        val shape = RoundedCornerShape(backgroundType.cornerRadius.dp)
        Box(
          modifier =
            modifier
              .resId(resolvedTestTag)
              .size(iconModel.totalSize.dp)
              .alpha(if (enabled) 1f else 0.5f)
              .clip(shape)
              .scalingClickable(
                enabled = enabled,
                onClick = onClick
              )
              .background(
                color =
                  if (backgroundType.color == IconBackgroundType.Square.Color.InverseBackground) {
                    WalletTheme.colors.inverseBackground
                  } else {
                    WalletTheme.colors.foreground10
                  },
                shape = shape
              ),
          contentAlignment = Alignment.Center
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
        Box(
          modifier =
            modifier
              .resId(resolvedTestTag)
              .size(iconModel.totalSize.dp)
              .alpha(if (enabled) 1f else 0.5f)
              .scalingClickable(
                enabled = enabled,
                onClick = onClick
              ),
          contentAlignment = Alignment.Center
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
      val isDesignSystemV2Enabled = true
      val labelType = if (isDesignSystemV2Enabled) LabelType.Body3Mono else LabelType.Title3

      Spacer(Modifier.height(8.dp))
      Label(
        text = it,
        type = labelType,
        treatment = if (enabled) LabelTreatment.Primary else LabelTreatment.Disabled
      )
    }
  }
}
