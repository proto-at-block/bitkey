package build.wallet.ui.components.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.AutoResizedLabel
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle.CARD_GROUP
import build.wallet.ui.model.list.ListGroupStyle.CARD_GROUP_DIVIDER
import build.wallet.ui.model.list.ListGroupStyle.CARD_ITEM
import build.wallet.ui.model.list.ListGroupStyle.DIVIDER
import build.wallet.ui.model.list.ListGroupStyle.NONE
import build.wallet.ui.model.list.ListGroupStyle.THREE_COLUMN_CARD_ITEM
import build.wallet.ui.model.list.ListGroupStyle.THREE_COLUMN_CARD_ITEM_LARGE
import build.wallet.ui.model.list.ListGroupStyle.THREE_COLUMN_KEYPAD_ITEM
import build.wallet.ui.model.list.ListItemAccessory.CircularIconAccessory
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ListGroup(
  model: ListGroupModel,
  modifier: Modifier = Modifier,
  collapseContent: Boolean = false,
) {
  Column {
    when (model.style) {
      CARD_ITEM -> CardListGroup(model)
      DIVIDER -> RegularListGroup(model, modifier, showsDivider = true) {
        ListItem(model = it, collapseContent = collapseContent)
      }
      NONE -> RegularListGroup(model, modifier, showsDivider = false) {
        ListItem(model = it, collapseContent = collapseContent)
      }
      CARD_GROUP, CARD_GROUP_DIVIDER ->
        Card {
          RegularListGroup(
            model = model,
            showsDivider = model.style == CARD_GROUP_DIVIDER,
            addsVerticalPadding = true
          ) {
            Row(
              modifier = Modifier.defaultMinSize(minHeight = 64.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              ListItem(model = it)
            }
          }
        }

      THREE_COLUMN_CARD_ITEM -> FixedColumnCardListGroup(model, columnCount = 3)

      THREE_COLUMN_CARD_ITEM_LARGE -> FixedColumnCardListGroup(
        model,
        columnCount = 3,
        cardWidth = 98,
        cardHeight = 80
      )

      THREE_COLUMN_KEYPAD_ITEM -> FixedColumnKeypadListGroup(model, columnCount = 3)
    }
    model.explainerSubtext?.let {
      Label(
        modifier = Modifier.padding(horizontal = 16.dp)
          .padding(top = 8.dp),
        text = it,
        treatment = LabelTreatment.Secondary,
        type = LabelType.Body4Regular
      )
    }
  }
}

@Composable
private fun FixedColumnKeypadListGroup(
  model: ListGroupModel,
  columnCount: Int,
) {
  if (!LocalDesignSystemUpdatesEnabled.current) {
    FixedColumnCardListGroup(model, columnCount = columnCount)
    return
  }

  LazyVerticalGrid(
    columns = GridCells.Fixed(count = columnCount),
    modifier = Modifier.heightIn(max = 512.dp)
  ) {
    items(model.items.size) { index ->
      DsV2FixedColumnKeypadListItem(item = model.items[index])
    }
  }
}

@Composable
private fun DsV2FixedColumnKeypadListItem(item: ListItemModel) {
  val interactionSource = remember { MutableInteractionSource() }
  val scope = rememberCoroutineScope()
  val isEnabled = item.enabled && item.onClick != null
  var isVisuallyPressed by remember { mutableStateOf(false) }
  var releaseVisualPressJob by remember { mutableStateOf<Job?>(null) }
  val surfaceScale by animateFloatAsState(
    targetValue = if (isVisuallyPressed) 1.06f else 1f,
    animationSpec =
      if (isVisuallyPressed) {
        snap()
      } else {
        tween(durationMillis = KEYPAD_LIST_PRESS_OUT_DURATION_MS)
      },
    label = "keypad-list-item-surface-scale"
  )

  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(72.dp)
        .pointerInput(isEnabled, item.title) {
          if (!isEnabled) return@pointerInput

          awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            releaseVisualPressJob?.cancel()
            isVisuallyPressed = true
            waitForUpOrCancellation()
            releaseVisualPressJob =
              scope.launch {
                delay(KEYPAD_LIST_MINIMUM_VISUAL_DURATION_MS)
                isVisuallyPressed = false
              }
          }
        }
        .clickable(
          interactionSource = interactionSource,
          indication = null,
          enabled = isEnabled,
          onClick = { item.onClick?.invoke() }
        ),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier =
        Modifier
          .zIndex(if (isVisuallyPressed) 1f else 0f)
          .graphicsLayer {
            scaleX = surfaceScale
            scaleY = surfaceScale
          }
          .fillMaxSize()
          .padding(horizontal = 2.dp, vertical = 2.dp)
          .background(
            color = WalletTheme.colors.secondary,
            shape = CircleShape
          )
          .thenIf(item.selected) {
            Modifier.border(
              width = 1.dp,
              color = WalletTheme.colors.foreground,
              shape = CircleShape
            )
          },
      contentAlignment = Alignment.Center
    ) {
      val leadingIconAccessory = item.leadingAccessory as? IconAccessory
      if (leadingIconAccessory != null) {
        IconImage(
          model =
            leadingIconAccessory.model.copy(
              text = leadingIconAccessory.model.text ?: item.title
            ),
          color = WalletTheme.colors.secondaryForeground
        )
      } else {
        AutoResizedLabel(
          modifier = Modifier.padding(horizontal = 8.dp),
          text = item.title,
          type = item.titleType ?: LabelType.Keypad,
          treatment = LabelTreatment.Unspecified,
          color = WalletTheme.colors.secondaryForeground,
          allowFontScaling = item.allowFontScaling
        )
      }
    }
  }
}

@Composable
private fun FixedColumnCardListGroup(
  model: ListGroupModel,
  columnCount: Int,
  cardWidth: Int = 80,
  cardHeight: Int = 64,
) {
  LazyVerticalGrid(
    columns = GridCells.Fixed(count = columnCount),
    contentPadding = PaddingValues(8.dp),
    modifier = Modifier.heightIn(max = 512.dp)
  ) {
    items(model.items.size) { index ->
      val item = model.items[index]
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(8.dp)
      ) {
        Card(
          modifier =
            Modifier
              .height(cardHeight.dp)
              .width(cardWidth.dp)
              .thenIf(item.selected) {
                Modifier
                  .border(
                    width = 2.dp,
                    color = WalletTheme.colors.foreground,
                    shape = RoundedCornerShape(16.dp)
                  )
              },
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
          backgroundColor = WalletTheme.colors.foreground.copy(alpha = 0.03f)
        ) {
          ListItem(model = item)
        }
      }
    }
  }
}

private const val KEYPAD_LIST_PRESS_OUT_DURATION_MS = 120
private const val KEYPAD_LIST_MINIMUM_VISUAL_DURATION_MS = 32L

@Composable
private fun CardListGroup(model: ListGroupModel) {
  Column {
    model.header?.let { header ->
      ListSectionHeader(title = header, treatment = model.headerTreatment)
    }
    model.items.forEachIndexed { index, item ->
      Card(backgroundColor = WalletTheme.colors.secondary) {
        ListItem(model = item)
      }

      if (index < model.items.lastIndex) {
        Spacer(Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun RegularListGroup(
  model: ListGroupModel,
  modifier: Modifier = Modifier,
  showsDivider: Boolean,
  addsVerticalPadding: Boolean = false,
  listItem: @Composable ((ListItemModel) -> Unit),
) {
  val addsCircularAccessorySpacing =
    !showsDivider &&
      model.items.size > 1 &&
      model.items.all { item ->
        when (val accessory = item.leadingAccessory) {
          is CircularIconAccessory -> true
          is IconAccessory -> accessory.model.iconBackgroundType is IconBackgroundType.Circle
          else -> false
        }
      }

  Column(modifier) {
    model.header?.let { header ->
      ListSectionHeader(
        modifier =
          Modifier
            .padding(
              top = if (addsVerticalPadding) 12.dp else 0.dp,
              bottom = if (model.items.isEmpty()) 16.dp else 0.dp
            ),
        title = header,
        treatment = model.headerTreatment
      )
    }
    model.items.forEachIndexed { index, item ->
      listItem(item)
      if (index < model.items.lastIndex) {
        when {
          showsDivider -> Divider()
          addsCircularAccessorySpacing -> Spacer(Modifier.height(24.dp))
        }
      }
    }
    model.footerButton?.let { buttonModel ->
      Button(
        modifier =
          Modifier
            .padding(
              top = 8.dp,
              bottom = if (addsVerticalPadding) 20.dp else 0.dp
            )
            .height(40.dp),
        model = buttonModel,
        cornerRadius = 12.dp
      )
    }
  }
}
