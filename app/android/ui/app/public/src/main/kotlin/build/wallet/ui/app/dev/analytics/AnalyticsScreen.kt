package build.wallet.ui.app.dev.analytics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.dev.analytics.AnalyticsBodyModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.list.ListItem
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel.Size
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryDestructive
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList

@Composable
fun AnalyticsScreen(model: AnalyticsBodyModel) {
  BackHandler(onBack = model.onBack)
  Column(
    modifier =
      Modifier
        .padding(horizontal = 20.dp)
        .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Toolbar(
      model =
        ToolbarModel(
          leadingAccessory = BackAccessory(onClick = model.onBack),
          middleAccessory = ToolbarMiddleAccessoryModel(title = "Analytics")
        )
    )
    Spacer(Modifier.height(24.dp))
    Card {
      Button(
        text = "Clear events",
        treatment = TertiaryDestructive,
        size = Size.Footer,
        onClick = Click.StandardClick { model.onClear() }
      )
    }
    Spacer(Modifier.height(24.dp))
    EventsCard(model.events)
  }
}

@Composable
private fun EventsCard(events: ImmutableList<ListItemModel>) {
  Card {
    LazyColumn {
      items(events) {
        ListItem(model = it)
        Divider()
      }
    }
  }
}
