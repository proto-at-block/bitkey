package build.wallet.analytics.events

import build.wallet.analytics.v1.Event
import build.wallet.analytics.v1.EventBundle
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.f8e.analytics.EventTrackerF8eClientMock
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AnalyticsEventProcessorImplTests : FunSpec({
  val eventTrackerMock = EventTrackerF8eClientMock(turbines::create)

  test("successful send returns true") {
    eventTrackerMock.trackedEventResult = Ok(Unit)
    val eventSender = AnalyticsEventProcessorImpl(eventTrackerMock)
    val event = QueueAnalyticsEvent(f8eEnvironment = Production, event = Event())

    eventSender.processBatch(listOf(event)).unwrap()
    eventTrackerMock.trackedEvents.awaitItem().shouldBe(EventBundle(listOf(event.event)))
  }

  test("failed send returns false") {
    eventTrackerMock.trackedEventResult = Err(NetworkError(Throwable("uh oh!")))
    val processor = AnalyticsEventProcessorImpl(eventTrackerMock)
    val event = QueueAnalyticsEvent(f8eEnvironment = Production, event = Event())

    processor.processBatch(listOf(event)).shouldBeErrOfType<NetworkError>()
    eventTrackerMock.trackedEvents.awaitItem().shouldBe(EventBundle(listOf(event.event)))
  }
})
