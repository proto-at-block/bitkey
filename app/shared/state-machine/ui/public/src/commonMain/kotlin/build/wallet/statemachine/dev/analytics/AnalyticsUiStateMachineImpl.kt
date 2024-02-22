package build.wallet.statemachine.dev.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventStore
import build.wallet.analytics.v1.Event
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.model.list.ListItemModel
import kotlinx.collections.immutable.toImmutableList

class AnalyticsUiStateMachineImpl(
  private val eventStore: EventStore,
) : AnalyticsUiStateMachine {
  @Composable
  override fun model(props: Props): BodyModel {
    val events by remember {
      eventStore.events()
    }.collectAsState(emptyList())

    var presentedEventDetail: Event? by remember { mutableStateOf(null) }

    return when (val presentedEvent = presentedEventDetail) {
      null ->
        AnalyticsBodyModel(
          onClear = {
            eventStore.clear()
          },
          events =
            events.map { event ->
              ListItemModel(
                title = event.event_time,
                secondaryText = event.action.name,
                secondarySideText = event.screen_id,
                onClick = {
                  presentedEventDetail = event
                }
              )
            }.toImmutableList(),
          onBack = props.onBack
        )

      else ->
        EventBodyModel(
          onBack = {
            presentedEventDetail = null
          },
          event = presentedEvent
        )
    }
  }
}
