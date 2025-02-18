@file:Suppress("TooManyFunctions")

package build.wallet.ui.components.toolbar

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toIntSize
import bitkey.shared.ui_core_public.generated.resources.Res
import bitkey.shared.ui_core_public.generated.resources.beneficiary_onboarding_start
import bitkey.shared.ui_core_public.generated.resources.bitkey_gallery
import bitkey.shared.ui_core_public.generated.resources.how_inheritance_works
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.components.icon.iconStyle
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Secondary
import build.wallet.ui.compose.getScreenSize
import build.wallet.ui.compose.thenIfNotNull
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.ButtonAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.model.toolbar.ToolbarModel.HeroContent
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.imageResource

@Composable
fun Toolbar(
  model: ToolbarModel,
  modifier: Modifier = Modifier,
) {
  Toolbar(
    modifier = modifier,
    leadingContent = {
      model.leadingAccessory?.let {
        ToolbarAccessory(it)
      }
    },
    middleContent = {
      model.middleAccessory?.let { middleAccessory ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Label(text = middleAccessory.title, type = LabelType.Title2)
          middleAccessory.subtitle?.let {
            Label(text = it, type = LabelType.Title3, treatment = Secondary)
          }
        }
      }
    },
    trailingContent = {
      model.trailingAccessory?.let {
        ToolbarAccessory(it)
      }
    },
    backgroundDrawable =
      when (model.heroContent) {
        HeroContent.InheritanceSetup -> Res.drawable.beneficiary_onboarding_start
        HeroContent.InheritanceExplainer -> Res.drawable.how_inheritance_works
        HeroContent.PromoCodeHeader -> Res.drawable.bitkey_gallery
        else -> null
      }
  )
}

@Composable
fun ToolbarAccessory(model: ToolbarAccessoryModel) {
  when (model) {
    is ButtonAccessory -> Button(model.model)
    is IconAccessory ->
      IconButton(
        iconModel =
          IconModel(
            icon = (model.model.iconModel.iconImage as IconImage.LocalImage).icon,
            iconSize = model.model.iconModel.iconSize,
            iconBackgroundType = model.model.iconModel.iconBackgroundType,
            iconTint = model.model.iconModel.iconTint
          ),
        color =
          WalletTheme.iconStyle(
            icon = model.model.iconModel.iconImage,
            color = Color.Unspecified,
            tint = model.model.iconModel.iconTint
          ).color,
        text = model.model.iconModel.text,
        enabled = model.model.enabled,
        onClick = { model.model.onClick.invoke() }
      )
  }
}

/**
 * Tool bar component that allows displaying arbitrary leading, middle, and/or
 * trailing contents as slots.
 *
 *  ```
 * ┌────────────────────────────────────────────┐
 * │ ┌────────────┐┌────────────┐┌────────────┐ │
 * │ │  leading   ││   middle   ││  trailing  │ │
 * │ └────────────┘└────────────┘└────────────┘ │
 * └────────────────────────────────────────────┘
 * ```
 *
 */
@Composable
fun Toolbar(
  modifier: Modifier = Modifier,
  leadingContent: @Composable (() -> Unit)? = null,
  middleContent: @Composable (() -> Unit)? = null,
  trailingContent: @Composable (() -> Unit)? = null,
  backgroundDrawable: DrawableResource? = null,
) {
  val screenSize = getScreenSize()

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .thenIfNotNull(backgroundDrawable) {
        val img = imageResource(it)
        val width = with(LocalDensity.current) { screenSize.width.toPx() }
        val scale = width / img.width
        val height = with(LocalDensity.current) { (img.height * scale).toDp() }

        drawBehind {
          val xOffset = (screenSize.width.toPx() - size.width) / 2
          drawImage(
            image = img,
            dstOffset = Offset(x = -xOffset, y = 0f).round(),
            dstSize = Size(width, height.toPx()).toIntSize()
          )
        }.height(height)
      },
    contentAlignment = Alignment.TopCenter
  ) {
    ToolbarContainer {
      Box(
        modifier = modifier.fillMaxWidth()
      ) {
        ToolbarSlotBox(modifier = Modifier.align(Alignment.CenterStart)) {
          leadingContent?.invoke()
        }
        ToolbarSlotBox(modifier = Modifier.align(Alignment.Center)) {
          middleContent?.invoke()
        }
        ToolbarSlotBox(modifier = Modifier.align(Alignment.CenterEnd)) {
          trailingContent?.invoke()
        }
      }
    }
  }
}

@Composable
fun EmptyToolbar(modifier: Modifier = Modifier) {
  Toolbar(modifier = modifier)
}

@Composable
private fun BoxScope.ToolbarSlotBox(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(
    modifier = modifier,
    content = content
  )
}

/**
 * Slot-based container component for toolbar.
 */
@Composable
private fun ToolbarContainer(content: @Composable () -> Unit) {
  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(ToolbarHeight),
    contentAlignment = Alignment.CenterStart
  ) {
    content()
  }
}

private val ToolbarHeight = 48.dp
