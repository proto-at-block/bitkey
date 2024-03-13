package build.wallet.statemachine.home

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.v1.Action
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.AppFunctionalityStatusProviderMock
import build.wallet.availability.F8eUnreachable
import build.wallet.availability.InactiveApp
import build.wallet.availability.InternetUnreachable
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment
import build.wallet.statemachine.core.test
import build.wallet.statemachine.status.HomeStatusBannerUiProps
import build.wallet.statemachine.status.HomeStatusBannerUiStateMachineImpl
import build.wallet.time.ClockFake
import build.wallet.time.DateTimeFormatterMock
import build.wallet.time.TimeZoneProviderMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class HomeStatusBannerUiStateMachineImplTests : FunSpec({
  val appFunctionalityStatusProvider = AppFunctionalityStatusProviderMock()
  val eventTracker = EventTrackerMock(turbines::create)
  val stateMachine =
    HomeStatusBannerUiStateMachineImpl(
      appFunctionalityStatusProvider = appFunctionalityStatusProvider,
      dateTimeFormatter = DateTimeFormatterMock(),
      timeZoneProvider = TimeZoneProviderMock(),
      clock = ClockFake(),
      eventTracker = eventTracker
    )

  val propsOnBannerClickCalls = turbines.create<Unit>("props onBannerClick calls")
  val props =
    HomeStatusBannerUiProps(
      f8eEnvironment = F8eEnvironment.Production,
      onBannerClick = { propsOnBannerClickCalls.add(Unit) }
    )

  beforeEach {
    appFunctionalityStatusProvider.reset()
  }

  test("Null when FullFunctionality") {
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
    }
  }

  test("Model when LimitedFunctionality - F8eUnreachable") {
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
      appFunctionalityStatusProvider.appFunctionalityStatusFlow.emit(
        AppFunctionalityStatus.LimitedFunctionality(
          cause = F8eUnreachable(Instant.DISTANT_PAST)
        )
      )

      awaitItem().shouldNotBeNull().apply {
        title.shouldBe("Unable to reach Bitkey services")
        subtitle.shouldBe("Some features may not be available")
        onClick?.invoke()
        propsOnBannerClickCalls.awaitItem()
      }
    }
  }

  test("Model when LimitedFunctionality - Inactive App") {
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
      appFunctionalityStatusProvider.appFunctionalityStatusFlow.emit(
        AppFunctionalityStatus.LimitedFunctionality(cause = InactiveApp)
      )

      awaitItem().shouldNotBeNull().apply {
        title.shouldBe("Limited Functionality")
        subtitle.shouldBe("Your wallet is active on another phone")
        onClick?.invoke()
        propsOnBannerClickCalls.awaitItem()
      }

      eventTracker.eventCalls.awaitItem().action
        .shouldBe(Action.ACTION_APP_BECAME_INACTIVE)
    }
  }

  test("Model when LimitedFunctionality - InternetUnreachable") {
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
      appFunctionalityStatusProvider.appFunctionalityStatusFlow.emit(
        AppFunctionalityStatus.LimitedFunctionality(
          cause =
            InternetUnreachable(
              Instant.DISTANT_PAST,
              Instant.DISTANT_PAST
            )
        )
      )

      awaitItem().shouldNotBeNull().apply {
        title.shouldBe("Offline")
        subtitle.shouldBe("Balance last updated date-time")
        onClick?.invoke()
        propsOnBannerClickCalls.awaitItem()
      }
    }
  }

  test("Model when LimitedFunctionality - Emergency Access Kit") {
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
      appFunctionalityStatusProvider.appFunctionalityStatusFlow.emit(
        AppFunctionalityStatus.LimitedFunctionality(
          cause = build.wallet.availability.EmergencyAccessMode
        )
      )

      awaitItem().shouldNotBeNull().apply {
        title.shouldBe("Limited Functionality")
        subtitle.shouldBe("Emergency Access Kit")
        onClick?.invoke()
        propsOnBannerClickCalls.awaitItem()
      }
    }
  }
})
