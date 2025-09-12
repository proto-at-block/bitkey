package build.wallet.analytics.events

import build.wallet.analytics.v1.Event
import build.wallet.platform.config.AppVariant
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

class EventStoreImplTest : FunSpec({
  test("event store no-ops when not on debug variant") {
    val event = Event()
    val eventStore = EventStoreImpl(
      appVariant = AppVariant.Customer
    )

    eventStore.add(event)
    val eventList = eventStore.events().first()
    eventList.size.shouldBe(0)
    eventList.shouldBe(listOf())
  }

  test("events are stored when on debug variant") {
    val eventStore = EventStoreImpl(
      appVariant = AppVariant.Development
    )
    val event = Event()

    eventStore.add(event)
    val eventList = eventStore.events().first()
    eventList.size.shouldBe(1)
    eventList.shouldBe(listOf(event))
  }
})
