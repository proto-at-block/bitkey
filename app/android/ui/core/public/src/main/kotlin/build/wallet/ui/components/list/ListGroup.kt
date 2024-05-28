package build.wallet.ui.components.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.compose.collections.immutableListOf
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListGroupStyle.CARD_GROUP
import build.wallet.ui.model.list.ListGroupStyle.CARD_GROUP_DIVIDER
import build.wallet.ui.model.list.ListGroupStyle.CARD_ITEM
import build.wallet.ui.model.list.ListGroupStyle.DIVIDER
import build.wallet.ui.model.list.ListGroupStyle.NONE
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun ListGroup(
  model: ListGroupModel,
  modifier: Modifier = Modifier,
  collapseContent: Boolean = false,
) {
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

    ListGroupStyle.THREE_COLUMN_CARD_ITEM -> FixedColumnCardListGroup(model, columnCount = 3)
  }
}

@Composable
private fun FixedColumnCardListGroup(
  model: ListGroupModel,
  columnCount: Int,
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
              .height(64.dp)
              .width(80.dp)
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

@Composable
private fun CardListGroup(model: ListGroupModel) {
  Column {
    model.header?.let { header ->
      ListSectionHeader(title = header, treatment = model.headerTreatment)
    }
    model.items.forEachIndexed { index, item ->
      Card(backgroundColor = Color.Black.copy(alpha = 0.03f)) {
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
      if (showsDivider && index < model.items.lastIndex) {
        Divider()
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

@Preview
@Composable
internal fun ListSectionForPreview(
  showHeader: Boolean,
  style: ListGroupStyle = NONE,
  collapsed: Boolean = false,
) {
  PreviewWalletTheme {
    Box(modifier = Modifier.background(WalletTheme.colors.foreground10)) {
      ListGroup(
        collapseContent = collapsed,
        model =
          ListGroupModel(
            header = "Header".takeIf { showHeader },
            items =
              immutableListOf(
                ListItemModel(
                  title = "Title 1"
                ),
                ListItemModel(
                  title = "Title 2",
                  secondaryText = "Secondary text 2"
                ),
                ListItemModel(
                  title = "Title 3",
                  sideText = "Side text 3"
                )
              ),
            style = style
          )
      )
    }
  }
}
