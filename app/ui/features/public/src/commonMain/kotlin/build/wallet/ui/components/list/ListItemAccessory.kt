package build.wallet.ui.components.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.bitkey_corian
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.icon.dp
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.loadingScrim
import build.wallet.ui.components.loading.LoadingIndicator
import build.wallet.ui.components.switch.Switch
import build.wallet.ui.compose.resId
import build.wallet.ui.compose.resolveTestTag
import build.wallet.ui.compose.switchTestTag
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessory.*
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.LocalIsPreviewTheme
import org.jetbrains.compose.resources.imageResource
import kotlin.random.Random

@Composable
internal fun ListItemAccessory(
  model: ListItemAccessory,
  isLoading: Boolean = false,
  parentTestTag: String? = null,
) {
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current

  when (model) {
    is IconAccessory ->
      {
        val isDesignSystemV2Chevron = isDesignSystemV2Enabled && model.isChevronAccessory()
        val resolvedIconModel =
          if (isDesignSystemV2Chevron) {
            model.model.copy(iconSize = IconSize.Accessory)
          } else {
            model.model
          }
        val resolvedOpticalOffsetX =
          if (isDesignSystemV2Chevron) {
            4
          } else {
            model.opticalOffsetX
          }

        IconImage(
          modifier =
            Modifier
              .loadingScrim(isLoading)
              .resId(resolveTestTag(model.testTag, parentTestTag ?: "list-item-icon-accessory"))
              .padding(model.iconPadding?.dp ?: 0.dp)
              .offset {
                IntOffset(
                  x = (resolvedOpticalOffsetX ?: 0).dp.roundToPx(),
                  y = 0
                )
              }
              .let { modifier ->
                model.onClick?.let {
                  modifier.clickable(
                    onClick = it
                  )
                } ?: modifier
              },
          model = resolvedIconModel
        )
      }

    is SwitchAccessory -> {
      val resolvedSwitchTestTag = resolveTestTag(
        model.model.testTag,
        parentTestTag ?: switchTestTag(descriptor = "list-item")
      )
      Switch(
        model = model.model.copy(testTag = resolvedSwitchTestTag)
      )
    }
    is ButtonAccessory -> {
      Box(
        modifier = Modifier.resId(parentTestTag)
      ) {
        Button(
          model = model.model
        )
      }
    }
    is TextAccessory ->
      Label(
        modifier = Modifier
          .loadingScrim(isLoading)
          .resId(parentTestTag?.let { "$it-text" })
          .padding(end = 12.dp),
        text = model.text,
        type = LabelType.Body2Regular
      )
    is CircularCharacterAccessory ->
      CircularCharacterAccessory(
        model = model,
        modifier = Modifier.resId(parentTestTag?.let { "$it-circular-character" })
      )
    is CircularIconAccessory ->
      CircularIconAccessoryView(
        model = model,
        modifier = Modifier.resId(parentTestTag?.let { "$it-circular-icon" })
      )
    is ContactAvatarAccessory ->
      ContactAvatarAccessory(
        model = model,
        modifier = Modifier.resId(parentTestTag?.let { "$it-contact-avatar" })
      )
    is CheckAccessory ->
      CheckIconAccessory(
        modifier = Modifier.resId(parentTestTag?.let { "$it-check" }),
        isChecked = model.isChecked
      )
  }
}

private fun IconAccessory.isChevronAccessory(): Boolean {
  val iconImage = model.iconImage
  return iconImage is build.wallet.ui.model.icon.IconImage.LocalImage &&
    iconImage.icon == Icon.SmallIconCaretRight
}

@Composable
private fun CircularCharacterAccessory(
  model: CircularCharacterAccessory,
  modifier: Modifier = Modifier,
) {
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current

  Box(
    modifier =
      modifier
        .padding(end = 4.dp)
  ) {
    Box(
      modifier =
        Modifier
          .size(model.circleSize.dp)
          .background(
            color = WalletTheme.colors.foreground10,
            shape = CircleShape
          ),
      contentAlignment = Alignment.Center
    ) {
      Label(
        text = model.character.toString(),
        type = model.characterType.regularizedForDesignSystemV2ListItems(isDesignSystemV2Enabled)
      )
    }
  }
}

@Composable
private fun CircularIconAccessoryView(
  model: build.wallet.ui.model.list.ListItemAccessory.CircularIconAccessory,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .padding(end = 4.dp)
  ) {
    Box(
      modifier =
        Modifier
          .size(model.circleSize.dp)
          .background(
            color = WalletTheme.colors.foreground10,
            shape = CircleShape
          ),
      contentAlignment = Alignment.Center
    ) {
      IconImage(
        model = IconModel(
          icon = model.icon,
          iconSize = model.iconSize,
          iconTint = model.iconTint
        )
      )
    }
  }
}

@Composable
private fun ContactAvatarAccessory(
  model: ContactAvatarAccessory,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .padding(end = 4.dp)
  ) {
    val bitmap = imageResource(Res.drawable.bitkey_corian)
    val offset = if (LocalIsPreviewTheme.current) {
      0
    } else {
      remember(model.initials) {
        Random.Default.nextInt(-bitmap.height, 0)
      }
    }
    Box(
      modifier =
        Modifier
          .size(54.dp)
          .drawWithCache {
            val circlePath = Path().apply {
              addOval(Rect(Offset.Zero, size))
            }
            onDrawWithContent {
              clipPath(circlePath) {
                drawImage(
                  image = bitmap,
                  topLeft = Offset(
                    x = 0f,
                    y = (offset + size.height).coerceAtMost(0f)
                  )
                )
              }
              drawContent()
            }
          },
      contentAlignment = Alignment.Center
    ) {
      Label(
        text = model.initials,
        type = LabelType.Label1Bold,
        color = Color.White,
        treatment = LabelTreatment.Unspecified
      )
      AnimatedVisibility(
        visible = model.isLoading,
        modifier = Modifier.align(Alignment.BottomEnd)
      ) {
        Box(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .size(IconSize.Accessory.dp)
            .background(
              color = WalletTheme.colors.primaryIconForeground,
              shape = CircleShape
            )
            .padding(2.dp)
            .background(
              color = WalletTheme.colors.bitkeyLoading,
              shape = CircleShape
            )
            .padding(2.dp)
        ) {
          LoadingIndicator(
            color = WalletTheme.colors.primaryIconForeground
          )
        }
      }
    }
  }
}

@Composable
private fun CheckIconAccessory(
  modifier: Modifier = Modifier,
  isChecked: Boolean,
) {
  val icon = if (isChecked) {
    Icon.SmallIconCheckboxSelected
  } else {
    Icon.SmallIconCheckbox
  }

  IconImage(
    modifier = modifier.size(24.dp),
    model = IconModel(
      icon = icon,
      iconSize = IconSize.Small
    )
  )
}

private fun LabelType.regularizedForDesignSystemV2ListItems(
  isDesignSystemV2Enabled: Boolean,
): LabelType =
  if (!isDesignSystemV2Enabled) {
    this
  } else {
    when (this) {
      LabelType.Body1Medium -> LabelType.Body1Regular
      LabelType.Body2Medium -> LabelType.Body2Regular
      LabelType.Body3Medium -> LabelType.Body3Regular
      LabelType.Body4Medium -> LabelType.Body4Regular
      else -> this
    }
  }
