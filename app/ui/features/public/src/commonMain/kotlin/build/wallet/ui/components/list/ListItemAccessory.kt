package build.wallet.ui.components.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.bitkey_corian
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.icon.dp
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.loadingScrim
import build.wallet.ui.components.loading.LoadingIndicator
import build.wallet.ui.components.switch.Switch
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessory.*
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.LocalIsPreviewTheme
import org.jetbrains.compose.resources.imageResource
import kotlin.random.Random

@Composable
internal fun ListItemAccessory(
  model: ListItemAccessory,
  isLoading: Boolean = false,
) {
  when (model) {
    is IconAccessory ->
      {
        IconImage(
          modifier =
            Modifier
              .loadingScrim(isLoading)
              .padding(model.iconPadding?.dp ?: 0.dp)
              .let { modifier ->
                model.onClick?.let {
                  modifier.clickable(
                    onClick = it
                  )
                } ?: modifier
              },
          model = model.model
        )
      }

    is SwitchAccessory -> Switch(model.model)
    is ButtonAccessory -> Button(model.model)
    is TextAccessory ->
      Label(
        modifier = Modifier
          .loadingScrim(isLoading)
          .padding(end = 12.dp),
        text = model.text,
        type = LabelType.Body2Regular
      )
    is CircularCharacterAccessory -> CircularCharacterAccessory(model)
    is ContactAvatarAccessory -> ContactAvatarAccessory(model)
    is CheckAccessory -> CircularCheckAccessory(isChecked = model.isChecked)
  }
}

@Composable
private fun CircularCharacterAccessory(model: CircularCharacterAccessory) {
  Box(
    modifier =
      Modifier
        .padding(end = 4.dp)
  ) {
    Box(
      modifier =
        Modifier
          .size(24.dp)
          .background(
            color = WalletTheme.colors.foreground10,
            shape = CircleShape
          ),
      contentAlignment = Alignment.Center
    ) {
      Label(
        text = model.character.toString(),
        type = LabelType.Label3
      )
    }
  }
}

@Composable
private fun ContactAvatarAccessory(model: ContactAvatarAccessory) {
  Box(
    modifier =
      Modifier
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
private fun CircularCheckAccessory(
  modifier: Modifier = Modifier,
  isChecked: Boolean,
) {
  val (color, width) = if (isChecked) {
    Pair(WalletTheme.colors.bitkeyPrimary, 4.dp)
  } else {
    Pair(WalletTheme.colors.foreground60, 2.dp)
  }

  Box(
    modifier = modifier
      .size(24.dp)
      .background(WalletTheme.colors.foreground10, CircleShape)
      .border(
        width = width,
        color = color,
        shape = CircleShape
      )
  )
}
