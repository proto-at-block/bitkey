package build.wallet.ui.components.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.bitkey_corian
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.platform.haptics.HapticsEffect
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.icon.dp
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.loadingScrim
import build.wallet.ui.components.loading.LoadingIndicator
import build.wallet.ui.components.switch.Switch
import build.wallet.ui.compose.LocalHaptics
import build.wallet.ui.compose.resId
import build.wallet.ui.compose.resolveTestTag
import build.wallet.ui.compose.switchTestTag
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessory.*
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.LocalIsPreviewTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.imageResource
import kotlin.random.Random

@Composable
internal fun ListItemAccessory(
  model: ListItemAccessory,
  isLoading: Boolean = false,
  parentTestTag: String? = null,
) {
  when (model) {
    is IconAccessory ->
      {
        val isCurrentChevron = model.isChevronAccessory()
        val resolvedIconModel =
          if (isCurrentChevron) {
            model.model.copy(iconSize = IconSize.Accessory)
          } else {
            model.model
          }
        val resolvedOpticalOffsetX =
          if (isCurrentChevron) {
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
    is CheckboxAccessory ->
      AnimatedCheckboxAccessory(
        modifier = Modifier
          .resId(resolveTestTag(model.testTag, parentTestTag?.let { "$it-checkbox" } ?: "list-item-checkbox"))
          .loadingScrim(isLoading),
        isChecked = model.isChecked,
        isEnabled = model.isEnabled,
        onClick = model.onClick
      )
  }
}

private fun IconAccessory.isChevronAccessory(): Boolean {
  val iconImage = model.iconImage
  return iconImage is build.wallet.ui.model.icon.IconImage.LocalImage &&
    iconImage.icon == Icon.CaretRight
}

@Composable
private fun CircularCharacterAccessory(
  model: CircularCharacterAccessory,
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
            color = when (model.backgroundColor) {
              CircularCharacterAccessory.BackgroundColor.Foreground10 -> WalletTheme.colors.foreground10
              CircularCharacterAccessory.BackgroundColor.SubtleBackground -> WalletTheme.colors.subtleBackground
            },
            shape = CircleShape
          ),
      contentAlignment = Alignment.Center
    ) {
      Label(
        text = model.character.toString(),
        type = model.characterType.regularizedForListItems()
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
            color = when (model.backgroundColor) {
              ListItemAccessory.CircularIconAccessory.BackgroundColor.Foreground10 -> WalletTheme.colors.foreground10
              ListItemAccessory.CircularIconAccessory.BackgroundColor.SubtleBackground -> WalletTheme.colors.subtleBackground
            },
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
private fun AnimatedCheckboxAccessory(
  modifier: Modifier = Modifier,
  isChecked: Boolean,
  isEnabled: Boolean,
  onClick: () -> Unit,
) {
  val fillAlphaProgress = animateFloatAsState(
    targetValue = if (isChecked) 1f else 0f,
    animationSpec = tween(
      durationMillis = 250,
      easing = LinearOutSlowInEasing
    ),
    label = "checkbox-fill-alpha-progress"
  )
  val fillScaleProgress = animateFloatAsState(
    targetValue = if (isChecked) 1f else 0f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMedium
    ),
    label = "checkbox-fill-scale-progress"
  )
  val checkProgress = animateFloatAsState(
    targetValue = if (isChecked) 1f else 0f,
    animationSpec = tween(
      durationMillis = 200,
      easing = LinearEasing
    ),
    label = "checkbox-check-progress"
  )
  val checkedFillPath = remember { checkedFillPath() }
  val checkPath = remember { checkPath() }
  val checkboxColor = WalletTheme.colors.foreground
  val cutoutColor = WalletTheme.colors.background
  val haptics = LocalHaptics.current
  val scope = rememberStableCoroutineScope()
  val onCheckboxClick = remember(isEnabled, onClick, haptics, scope) {
    {
      if (isEnabled) {
        haptics?.let {
          scope.launch { it.vibrate(HapticsEffect.Selection) }
        }
        onClick()
      }
    }
  }

  Box(
    modifier = modifier
      .size(width = 28.dp, height = 20.dp),
    contentAlignment = Alignment.CenterStart
  ) {
    Canvas(
      modifier = Modifier
        .size(20.dp)
        .semantics {
          role = Role.Checkbox
          toggleableState = ToggleableState(isChecked)
          if (!isEnabled) {
            disabled()
          }
          onClick {
            if (isEnabled) {
              onCheckboxClick()
            }
            isEnabled
          }
        }
        .then(
          if (isEnabled) {
            Modifier.pointerInput(onCheckboxClick) {
              detectTapGestures(onTap = { onCheckboxClick() })
            }
          } else {
            Modifier
          }
        )
    ) {
      val svgScale = size.width / CHECKBOX_SVG_SIZE
      val currentFillAlphaProgress = fillAlphaProgress.value.coerceIn(0f, 1f)
      val currentFillScaleProgress = fillScaleProgress.value.coerceAtLeast(0f)
      val currentCheckProgress = checkProgress.value.coerceIn(0f, 1f)
      val fillScale = CHECKBOX_CHECKED_FILL_MIN_SCALE +
        (1f - CHECKBOX_CHECKED_FILL_MIN_SCALE) * currentFillScaleProgress
      val center = Offset(size.width / 2f, size.height / 2f)

      scale(fillScale, pivot = center) {
        scale(svgScale, pivot = Offset.Zero) {
          drawPath(
            path = checkedFillPath,
            color = checkboxColor.copy(alpha = currentFillAlphaProgress)
          )
        }
      }
      drawRoundRect(
        color = checkboxColor.copy(alpha = 1f - currentFillAlphaProgress),
        topLeft = Offset(svgScale, svgScale),
        size = Size(18f * svgScale, 18f * svgScale),
        cornerRadius = CornerRadius(3f * svgScale, 3f * svgScale),
        style = Stroke(
          width = 2f * svgScale,
          cap = StrokeCap.Round
        )
      )
      clipRect(right = size.width * currentCheckProgress) {
        scale(svgScale, pivot = Offset.Zero) {
          drawPath(
            path = checkPath,
            color = cutoutColor
          )
        }
      }
    }
  }
}

private fun checkedFillPath(): Path =
  Path().apply {
    moveTo(16f, 0f)
    cubicTo(18.2091f, 0f, 20f, 1.79086f, 20f, 4f)
    lineTo(20f, 16f)
    cubicTo(20f, 18.2091f, 18.2091f, 20f, 16f, 20f)
    lineTo(4f, 20f)
    cubicTo(1.79086f, 20f, 0f, 18.2091f, 0f, 16f)
    lineTo(0f, 4f)
    cubicTo(0f, 1.79086f, 1.79086f, 0f, 4f, 0f)
    lineTo(16f, 0f)
    close()
  }

private fun checkPath(): Path =
  Path().apply {
    moveTo(8.99609f, 11.9766f)
    lineTo(6.25293f, 8.8418f)
    lineTo(4.74707f, 10.1582f)
    lineTo(8.24707f, 14.1582f)
    cubicTo(8.43741f, 14.3757f, 8.71292f, 14.5006f, 9.00195f, 14.5f)
    cubicTo(9.29109f, 14.4993f, 9.56646f, 14.3737f, 9.75586f, 14.1553f)
    lineTo(16.2559f, 6.65527f)
    lineTo(14.7441f, 5.34473f)
    lineTo(8.99609f, 11.9766f)
    close()
  }

private const val CHECKBOX_SVG_SIZE = 20f
private const val CHECKBOX_CHECKED_FILL_MIN_SCALE = 0.5f

private fun LabelType.regularizedForListItems(): LabelType =
  when (this) {
    LabelType.Body1Medium -> LabelType.Body1Regular
    LabelType.Body2Medium -> LabelType.Body2Regular
    LabelType.Body3Medium -> LabelType.Body3Regular
    LabelType.Body4Medium -> LabelType.Body4Regular
    else -> this
  }
