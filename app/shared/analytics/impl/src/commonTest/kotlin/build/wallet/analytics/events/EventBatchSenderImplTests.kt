package build.wallet.analytics.events

import build.wallet.analytics.v1.Event
import build.wallet.analytics.v1.EventBundle
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.f8e.analytics.EventTrackerServiceMock
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EventBatchSenderImplTests : FunSpec({
  val eventTrackerMock = EventTrackerServiceMock(turbines::create)

  test("successful send returns true") {
    eventTrackerMock.trackedEventResult = Ok(Unit)
    val eventSender = EventSenderImpl(eventTrackerMock)
    val event = QueueAnalyticsEvent(f8eEnvironment = Production, event = Event())

    eventSender.processBatch(listOf(event)).unwrap()
    eventTrackerMock.trackedEvents.awaitItem().shouldBe(EventBundle(listOf(event.event)))
  }

  test("failed send returns false") {
    eventTrackerMock.trackedEventResult = Err(NetworkError(Throwable("uh oh!")))
    val eventSender = EventSenderImpl(eventTrackerMock)
    val event = QueueAnalyticsEvent(f8eEnvironment = Production, event = Event())

    eventSender.processBatch(listOf(event)).shouldBeErrOfType<NetworkError>()
    eventTrackerMock.trackedEvents.awaitItem().shouldBe(EventBundle(listOf(event.event)))
  }
})
