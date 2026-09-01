package build.wallet.ui.app.moneyhome.gettingstarted

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedSectionModel
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedTileModel
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.list.ListHeader
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

private const val GETTING_STARTED_TILE_ASPECT_RATIO = 168f / 116f
private const val GETTING_STARTED_TILE_CHEVRON_SIZE_DP = 16
private val GETTING_STARTED_TILE_PEEK_WIDTH = 36.dp

@Composable
fun GettingStartedSection(
  modifier: Modifier = Modifier,
  model: GettingStartedSectionModel,
) {
  val sortedTiles = remember(model.incomplete, model.complete) {
    model.incomplete + model.complete
  }

  Column(modifier = modifier.fillMaxWidth()) {
    ListHeader(
      title = model.title,
      titleType = LabelType.Body3Mono
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val tileSpacing = 10.dp
      val tileWidth = if (sortedTiles.size > 2) {
        (maxWidth - tileSpacing - GETTING_STARTED_TILE_PEEK_WIDTH) / 2
      } else {
        (maxWidth - tileSpacing) / 2
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(tileSpacing)
      ) {
        sortedTiles.forEach { tile ->
          key(tile.id) {
            GettingStartedTile(
              task = tile,
              tileWidth = tileWidth
            )
          }
        }
      }
    }
  }
}

@Composable
private fun GettingStartedTile(
  task: GettingStartedTileModel,
  tileWidth: Dp,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isClickable = task.onClick != null
  val cornerRadius = 8.dp

  Box(
    modifier = Modifier
      .width(tileWidth)
      .aspectRatio(GETTING_STARTED_TILE_ASPECT_RATIO)
      .thenIf(isClickable) {
        Modifier.clickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = { task.onClick?.invoke() }
        )
      }
      .background(
        color = WalletTheme.colors.secondary,
        shape = RoundedCornerShape(cornerRadius)
      )
      .thenIf(task.isComplete) { Modifier.alpha(0.3f) }
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      task.leadingIcon?.let {
        IconImage(model = it)
      }

      if (!task.isComplete) {
        IconImage(
          modifier = Modifier.thenIf(!task.isEnabled) { Modifier.alpha(0.3f) },
          model = IconModel(
            icon = Icon.CaretRight,
            iconSize = IconSize.Custom(GETTING_STARTED_TILE_CHEVRON_SIZE_DP),
            iconTint = IconTint.On30
          )
        )
      }
    }

    Label(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(12.dp),
      text = task.title,
      type = LabelType.Body2Regular,
      treatment =
        when {
          task.isComplete -> LabelTreatment.Secondary
          task.isEnabled -> LabelTreatment.Primary
          else -> LabelTreatment.Disabled
        }
    )
  }
}
