package build.wallet.statemachine.home

import bitkey.recovery.fundslost.AtRiskCause
import bitkey.recovery.fundslost.FundsLostRiskLevel.AtRisk
import bitkey.recovery.fundslost.FundsLostRiskServiceFake
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.v1.Action
import build.wallet.availability.*
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.core.test
import build.wallet.statemachine.status.BannerContext
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
  val appFunctionalityService = AppFunctionalityServiceFake()
  val fundsLostRiskService = FundsLostRiskServiceFake()
  val eventTracker = EventTrackerMock(turbines::create)
  val stateMachine = HomeStatusBannerUiStateMachineImpl(
    appFunctionalityService = appFunctionalityService,
    dateTimeFormatter = DateTimeFormatterMock(),
    timeZoneProvider = TimeZoneProviderMock(),
    clock = ClockFake(),
    eventTracker = eventTracker,
    fundsLostRiskService = fundsLostRiskService
  )

  val propsOnBannerClickCalls = turbines.create<Unit>("props onBannerClick calls")
  val props = HomeStatusBannerUiProps(
    bannerContext = BannerContext.Home,
    onBannerClick = { propsOnBannerClickCalls.add(Unit) }
  )

  beforeEach {
    appFunctionalityService.reset()
    fundsLostRiskService.reset()
  }

  test("Null when FullFunctionality and no risk") {
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
    }
  }

  test("Model when LimitedFunctionality - F8eUnreachable") {
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
      appFunctionalityService.status.emit(
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
      appFunctionalityService.status.emit(
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
      appFunctionalityService.status.emit(
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

  test("Model when LimitedFunctionality - Emergency Exit Kit") {
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
      appFunctionalityService.status.emit(
        AppFunctionalityStatus.LimitedFunctionality(
          cause = build.wallet.availability.EmergencyExitMode
        )
      )

      awaitItem().shouldNotBeNull().apply {
        title.shouldBe("Limited Functionality")
        subtitle.shouldBe("Emergency Exit Mode")
        onClick?.invoke()
        propsOnBannerClickCalls.awaitItem()
      }
    }
  }

  test("Model when FullFunctionality - AtRisk") {
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
      appFunctionalityService.status.emit(
        AppFunctionalityStatus.FullFunctionality
      )
      fundsLostRiskService.riskLevel.emit(AtRisk(cause = AtRiskCause.MissingHardware))

      awaitItem().shouldNotBeNull().apply {
        title.shouldBe("Your wallet is at risk")
        subtitle.shouldBe("Add a Bitkey device to avoid losing funds →")
        onClick?.invoke()
        propsOnBannerClickCalls.awaitItem()
      }
    }
  }

  test("Model when LimitedFunctionality - AtRisk") {
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
      appFunctionalityService.status.emit(
        AppFunctionalityStatus.LimitedFunctionality(
          cause = F8eUnreachable(Instant.DISTANT_PAST)
        )
      )
      fundsLostRiskService.riskLevel.emit(AtRisk(cause = AtRiskCause.MissingHardware))

      awaitItem().shouldNotBeNull().apply {
        title.shouldBe("Unable to reach Bitkey services")
        subtitle.shouldBe("Some features may not be available")
        onClick?.invoke()
        propsOnBannerClickCalls.awaitItem()
      }
    }
  }

  test("Model when FullFunctionality - AtRisk due to ActiveSpendingKeysetMismatch") {
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
      appFunctionalityService.status.emit(
        AppFunctionalityStatus.FullFunctionality
      )
      fundsLostRiskService.riskLevel.emit(AtRisk(cause = AtRiskCause.ActiveSpendingKeysetMismatch))

      awaitItem().shouldNotBeNull().apply {
        title.shouldBe("Your wallet is at risk")
        subtitle.shouldBe("Fix your local data to protect your funds →")
        onClick?.invoke()
        propsOnBannerClickCalls.awaitItem()
      }
    }
  }
})
